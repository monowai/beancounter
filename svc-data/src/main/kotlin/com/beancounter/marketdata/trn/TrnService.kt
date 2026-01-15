package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.function.Consumer

/**
 * Interactions to created and read Trn objects.
 */
@Service
@Transactional
@Suppress("TooManyFunctions") // TrnService has 13 functions, threshold is 11
class TrnService(
    private val trnRepository: TrnRepository,
    private val trnInputMapper: TrnInputMapper,
    private val portfolioService: PortfolioService,
    private val trnMigrator: TrnMigrator,
    private val assetFinder: AssetFinder,
    private val systemUserService: SystemUserService,
    private val fxRateService: FxRateService
) {
    private val log = LoggerFactory.getLogger(TrnService::class.java)

    fun getPortfolioTrn(trnId: String): Collection<Trn> {
        val trn =
            trnRepository.findById(
                trnId
            )
        if (trn.isEmpty) {
            throw NotFoundException("Trn not found: $trnId")
        }
        val result = trn.map { transaction: Trn -> postProcess(setOf(transaction)) }
        return result.get()
    }

    fun save(
        portfolioId: String,
        trnRequest: TrnRequest
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        return save(
            portfolio,
            trnRequest
        )
    }

    fun save(
        portfolio: Portfolio,
        trnRequest: TrnRequest
    ): Collection<Trn> {
        // Figure out
        val saved =
            trnRepository.saveAll(
                trnInputMapper.convert(
                    portfolio,
                    trnRequest
                )
            )
        saved.forEach { trn ->
            trn.asset = assetFinder.hydrateAsset(trn.asset)
            trn.cashAsset = trn.cashAsset?.let { assetFinder.hydrateAsset(it) }
        }
        val results: MutableCollection<Trn> = mutableListOf()
        saved.forEach(Consumer { e: Trn -> results.add(e) })
        if (trnRequest.data.size == 1) {
            log.trace(
                "Wrote 1 transaction asset: ${trnRequest.data[0].assetId}, portfolio: ${portfolio.code}"
            )
        } else {
            log.trace(
                "Wrote ${results.size}/${trnRequest.data.size} transactions for ${portfolio.code}"
            )
        }
        return results
    }

    fun findForPortfolio(
        portfolioId: String,
        tradeDate: LocalDate
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        return findForPortfolio(portfolio, tradeDate)
    }

    fun findForPortfolio(
        portfolio: Portfolio,
        tradeDate: LocalDate
    ): Collection<Trn> {
        val results =
            trnRepository.findByPortfolioId(
                portfolio.id,
                tradeDate,
                TrnStatus.SETTLED,
                Sort.by("tradeDate").and(Sort.by("asset.code"))
            )
        log.trace("trns: ${results.size}, portfolio: ${portfolio.code}, asAt: $tradeDate")
        return postProcess(results)
    }

    /**
     * Find transactions for a portfolio with a specific status.
     */
    fun findByStatus(
        portfolioId: String,
        status: TrnStatus
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        val results = trnRepository.findByPortfolioIdAndStatus(portfolio.id, status)
        log.trace("trns: ${results.size}, portfolio: ${portfolio.code}, status: $status")
        return postProcess(results)
    }

    /**
     * Find all PROPOSED transactions for the current user across all their portfolios.
     */
    fun findProposedForUser(): Collection<Trn> {
        val user = systemUserService.getOrThrow()
        val results = trnRepository.findByStatusAndPortfolioOwner(TrnStatus.PROPOSED, user)
        log.trace("proposed trns: ${results.size}")
        return postProcess(results.toList())
    }

    /**
     * Count all PROPOSED transactions for the current user across all their portfolios.
     */
    fun countProposedForUser(): Long {
        val user = systemUserService.getOrThrow()
        val count = trnRepository.countByStatusAndPortfolioOwner(TrnStatus.PROPOSED, user)
        log.trace("proposed count: $count")
        return count
    }

    /**
     * Get the Cash Ladder for a specific cash asset in a portfolio.
     * Returns all SETTLED transactions where the cashAsset matches and
     * tradeDate is on or before today, showing all transactions that
     * impacted that cash position.
     */
    fun getCashLadder(
        portfolioId: String,
        cashAssetId: String
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        val today = LocalDate.now()
        val results =
            trnRepository.findByPortfolioIdAndCashAssetId(
                portfolio.id,
                cashAssetId,
                today,
                TrnStatus.SETTLED
            )
        log.trace("cash ladder: ${results.size} trns for portfolio: ${portfolio.code}, cashAsset: $cashAssetId")
        return postProcess(results.toList())
    }

    /**
     * Settle transactions by updating their status from PROPOSED to SETTLED.
     * @param portfolioId Portfolio ID
     * @param trnIds List of transaction IDs to settle
     * @return Updated transactions
     */
    fun settleTransactions(
        portfolioId: String,
        trnIds: List<String>
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        val settled = mutableListOf<Trn>()
        for (trnId in trnIds) {
            val trnOptional = trnRepository.findByPortfolioIdAndId(portfolio.id, trnId)
            if (trnOptional.isPresent) {
                val trn = trnOptional.get()
                if (trn.status == TrnStatus.PROPOSED) {
                    trn.status = TrnStatus.SETTLED
                    val saved = trnRepository.save(trn)
                    settled.add(saved)
                    log.debug("Settled transaction {} for portfolio {}", trnId, portfolio.code)
                } else {
                    log.warn(
                        "Cannot settle transaction {} - status is {} not PROPOSED",
                        trnId,
                        trn.status
                    )
                }
            } else {
                log.warn("Transaction {} not found in portfolio {}", trnId, portfolio.code)
            }
        }
        log.info("Settled {} transactions for portfolio {}", settled.size, portfolio.code)
        return postProcess(settled)
    }

    /**
     * Get the total investment amount for the current user in a specific month.
     * Sums all BUY and ADD transactions across all portfolios.
     *
     * @param yearMonth The month to calculate (e.g., YearMonth.now() for current month)
     * @return Total investment amount for the month
     */
    fun getMonthlyInvestment(yearMonth: YearMonth): BigDecimal {
        val user = systemUserService.getOrThrow()
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        val total =
            trnRepository.sumInvestmentsByOwnerAndDateRange(
                user,
                startDate,
                endDate,
                TrnStatus.SETTLED
            )
        log.trace("Monthly investment for $yearMonth: $total")
        return total
    }

    /**
     * Get all investment transactions for the current user in a specific month.
     * Returns individual BUY and ADD transactions across all portfolios.
     *
     * @param yearMonth The month to query
     * @return Collection of investment transactions
     */
    fun getMonthlyInvestmentTransactions(yearMonth: YearMonth): Collection<Trn> {
        val user = systemUserService.getOrThrow()
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        val results =
            trnRepository.findInvestmentsByOwnerAndDateRange(
                user,
                startDate,
                endDate,
                TrnStatus.SETTLED
            )
        log.trace("Monthly investment trns: ${results.size} in $yearMonth")
        return postProcess(results.toList())
    }

    /**
     * Get net investment for specific portfolios in a month, converted to target currency.
     * Calculates: BUY + ADD - SELL with FX conversion.
     *
     * @param yearMonth The month to calculate
     * @param portfolioIds List of portfolio IDs to include (empty = all user's portfolios)
     * @param targetCurrency Currency code to convert amounts to
     * @return Net investment amount in target currency
     */
    fun getMonthlyInvestmentConverted(
        yearMonth: YearMonth,
        portfolioIds: List<String>,
        targetCurrency: String
    ): BigDecimal {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        // Fetch transactions for the specified portfolios
        val transactions =
            if (portfolioIds.isEmpty()) {
                val user = systemUserService.getOrThrow()
                trnRepository.findInvestmentsByOwnerAndDateRange(
                    user,
                    startDate,
                    endDate,
                    TrnStatus.SETTLED
                )
            } else {
                trnRepository.findInvestmentsByPortfoliosAndDateRange(
                    portfolioIds,
                    startDate,
                    endDate,
                    TrnStatus.SETTLED
                )
            }

        if (transactions.isEmpty()) {
            return BigDecimal.ZERO
        }

        // Collect unique currencies needing conversion
        val currenciesNeeded =
            transactions
                .map { it.tradeCurrency.code }
                .filter { it != targetCurrency }
                .distinct()

        // Get FX rates if needed (key is "FROM:TO" format)
        val fxRates: Map<String, BigDecimal> =
            if (currenciesNeeded.isNotEmpty()) {
                val fxRequest = FxRequest(rateDate = "today")
                currenciesNeeded.forEach { fxRequest.add(IsoCurrencyPair(it, targetCurrency)) }
                val fxResponse = fxRateService.getRates(fxRequest, "")
                fxResponse.data.rates.entries.associate { (pair, fxRate) ->
                    "${pair.from}:${pair.to}" to fxRate.rate
                }
            } else {
                emptyMap()
            }

        // Sum with conversion
        var total = BigDecimal.ZERO
        for (trn in transactions) {
            val amount = trn.tradeAmount
            val fromCurrency = trn.tradeCurrency.code

            val convertedAmount =
                if (fromCurrency == targetCurrency) {
                    amount
                } else {
                    val rateKey = "$fromCurrency:$targetCurrency"
                    val rate = fxRates[rateKey] ?: BigDecimal.ONE
                    amount.multiply(rate)
                }

            // BUY is positive, SELL is negative (ADD excluded - represents transfers)
            total =
                when (trn.trnType) {
                    TrnType.BUY -> total.add(convertedAmount)
                    TrnType.SELL -> total.subtract(convertedAmount)
                    else -> total
                }
        }

        log.trace("Monthly investment for $yearMonth in $targetCurrency: $total (${transactions.size} trns)")
        return total
    }

    fun purge(portfolioId: String): Long {
        val portfolio = portfolioService.find(portfolioId)
        return purge(portfolio)
    }

    /**
     * Purge transactions for a portfolio.
     *
     * @param portfolio portfolio owned by the caller
     * @return number of deleted transactions
     */
    fun purge(portfolio: Portfolio): Long {
        log.debug(
            "Purging transactions for {}",
            portfolio.code
        )
        return trnRepository.deleteByPortfolioId(portfolio.id)
    }

    private fun postProcess(trns: List<Trn>): List<Trn> {
        log.trace("PostProcess ${trns.size} transactions")
        val assets =
            trns
                .flatMap {
                    listOfNotNull(
                        assetFinder.hydrateAsset(it.asset),
                        it.cashAsset?.let { cashAsset -> assetFinder.hydrateAsset(cashAsset) }
                    )
                }.associateBy { it.id }
        log.trace("PostProcess ${assets.size} assets")
        for (trn in trns) {
            trn.asset = assets[trn.asset.id]!!
            trn.cashAsset = trn.cashAsset?.let { assets[it.id] }
            val upgraded = trnMigrator.upgrade(trn)
            if (upgraded.version != trn.version) {
                trnRepository.save(upgraded)
            }
        }
        log.trace("Completed postProcess trns: ${trns.size}")
        return trns
    }

    internal fun postProcess(
        trns: Iterable<Trn>,
        secure: Boolean = true
    ): Collection<Trn> {
        if (secure) {
            val systemUser = systemUserService.getOrThrow()
            val filteredTrns =
                trns.filter {
                    portfolioService.isViewable(
                        systemUser,
                        it.portfolio
                    )
                }
            return postProcess(filteredTrns)
        } else {
            return postProcess(trns.toList())
        }
    }

    fun existing(trustedTrnEvent: TrustedTrnEvent): Collection<Trn> {
        val start = trustedTrnEvent.trnInput.tradeDate.minusDays(5)
        val endDate = trustedTrnEvent.trnInput.tradeDate.plusDays(20)
        return trnRepository.findExisting(
            trustedTrnEvent.portfolio.id,
            trustedTrnEvent.trnInput.assetId!!,
            trustedTrnEvent.trnInput.trnType,
            start,
            endDate
        )
    }

    fun delete(trnId: String): Collection<String> {
        val result =
            trnRepository.findById(trnId).orElseThrow {
                NotFoundException("Transaction not found: $trnId")
            }
        val deleted = mutableListOf<Trn>()
        if (portfolioService.canView(result.portfolio)) {
            trnRepository.delete(result)
            deleted.add(result)
        }
        return deleted.map { it.id }
    }

    fun patch(
        portfolioId: String,
        trnId: String,
        trnInput: TrnInput
    ): TrnResponse {
        val portfolio = portfolioService.find(portfolioId)
        return patch(
            portfolio,
            trnId,
            trnInput
        )
    }

    fun patch(
        portfolio: Portfolio,
        trnId: String,
        trnInput: TrnInput
    ): TrnResponse {
        val existing =
            getPortfolioTrn(
                // portfolio,
                trnId
            )
        val trn =
            trnInputMapper.map(
                portfolio,
                trnInput,
                existing.iterator().next()
            )
        trnRepository.save(trn)
        return TrnResponse(arrayListOf(trn))
    }
}