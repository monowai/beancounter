package com.beancounter.marketdata.trn.analysis

import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.classification.ClassificationService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.AssetMetaData
import com.beancounter.marketdata.trn.MonthlyIncomeResponse
import com.beancounter.marketdata.trn.TrnRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth

/**
 * Income reporting: the user's settled income trns, grouped and totalled by month.
 */
@Service
@Transactional
class IncomeAnalysisService(
    private val trnRepository: TrnRepository,
    private val systemUserService: SystemUserService,
    private val assetRepository: AssetRepository,
    private val classificationService: ClassificationService,
    private val portfolioService: PortfolioService,
    private val tradeAmountFx: TradeAmountFx
) {
    private val log = LoggerFactory.getLogger(IncomeAnalysisService::class.java)

    /**
     * Monthly income for the current user over the [months] ending at [endMonth].
     *
     * @param portfolioIds portfolios to scope to; empty means all the user's portfolios
     * @param groupBy "assetClass" (public API name for AccountingType category), "sector",
     *   "currency", or "market"
     * @param targetCurrency currency code every amount in the report is expressed in
     */
    fun getMonthlyIncome(
        months: Int,
        endMonth: YearMonth,
        portfolioIds: List<String>,
        groupBy: String,
        targetCurrency: String
    ): MonthlyIncomeResponse {
        val startMonth = endMonth.minusMonths((months - 1).toLong())
        val transactions = fetchIncomeTransactions(portfolioIds, startMonth.atDay(1), endMonth.atEndOfMonth())
        val tally =
            MonthlyIncomeTally(
                startMonth..endMonth,
                groupBy,
                targetCurrency,
                buildAssetMetaData(transactions.toList(), groupBy)
            )
        val amounts = tradeAmountFx.into(targetCurrency, transactions)
        transactions.forEach { tally.add(it, amounts.of(it)) }
        return tally.toResponse()
    }

    private fun fetchIncomeTransactions(
        portfolioIds: List<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Iterable<Trn> =
        if (portfolioIds.isEmpty()) {
            val user = systemUserService.getOrThrow()
            trnRepository.findIncomeByOwnerAndDateRange(user, startDate, endDate, TrnStatus.SETTLED)
        } else {
            // find() per id, so an unknown or unauthorized portfolio rejects the whole request
            portfolioIds.forEach(portfolioService::find)
            trnRepository.findIncomeByPortfoliosAndDateRange(portfolioIds, startDate, endDate, TrnStatus.SETTLED)
        }

    /**
     * Build a lightweight asset metadata lookup for income reporting.
     * Fetches only the fields needed for grouping without full asset hydration.
     */
    private fun buildAssetMetaData(
        transactions: List<Trn>,
        groupBy: String
    ): Map<String, AssetMetaData> {
        if (transactions.isEmpty()) return emptyMap()

        val assetIds = transactions.map { it.asset.id }.distinct()
        log.trace("Building metadata for ${assetIds.size} distinct assets")

        val assets = assetRepository.findAllById(assetIds).associateBy { it.id }

        // Sector data only when grouping by sector
        val sectorData =
            if (groupBy == "sector") {
                classificationService.getClassificationSummaries(assetIds)
            } else {
                emptyMap()
            }

        return assetIds.associateWith { assetId ->
            val asset = assets[assetId]
            AssetMetaData(
                assetId = assetId,
                code = asset?.code ?: assetId,
                name = asset?.name,
                category = asset?.category ?: UNKNOWN_GROUP,
                marketCode = asset?.marketCode ?: UNKNOWN_GROUP,
                sector = sectorData[assetId]?.sector
            )
        }
    }
}