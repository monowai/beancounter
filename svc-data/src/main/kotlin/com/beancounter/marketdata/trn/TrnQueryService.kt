package com.beancounter.marketdata.trn

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.portfolio.PortfolioService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Queries that return collections of transactions
 */
@Service
class TrnQueryService(
    val trnService: TrnService,
    val trnRepository: TrnRepository,
    val portfolioService: PortfolioService
) {
    private val log = LoggerFactory.getLogger(TrnQueryService::class.java)

    /**
     * Trades in a portfolio for the specified asset.
     *
     * @param portfolio trusted
     * @param assetId   filter by pk
     * @param tradeDate until this date inclusive
     * @return transactions that can be accumulated into a position
     */
    fun findAssetTrades(
        portfolio: Portfolio,
        assetId: String,
        tradeDate: LocalDate
    ): Collection<Trn> {
        val results =
            trnRepository.findByPortfolioIdAndAssetIdUpTo(
                portfolio.id,
                assetId,
                tradeDate
            )
        log.trace(
            "count: {}, portfolio: {}, asset: {}",
            results.size,
            portfolio.code,
            assetId
        )
        return trnService.postProcess(
            results,
            false
        )
    }

    private val eventFilter = listOf(TrnType.DIVI, TrnType.SPLIT)

    private val tradeFilter =
        arrayListOf(
            TrnType.BUY,
            TrnType.SELL,
            TrnType.SPLIT,
            TrnType.DEPOSIT,
            TrnType.WITHDRAWAL,
            TrnType.FX_BUY,
            TrnType.BALANCE,
            TrnType.ADD,
            TrnType.REDUCE,
            TrnType.COST_ADJUST,
            TrnType.EXPENSE,
            TrnType.INCOME,
            TrnType.DEDUCTION
        )

    fun findAssetTrades(
        portfolioId: String,
        assetId: String
    ): Collection<Trn> =
        findAssetTrades(
            portfolioService.find(portfolioId),
            assetId
        )

    fun findAssetTrades(
        portfolio: Portfolio,
        assetId: String
    ): Collection<Trn> =
        trnResponse(
            portfolio,
            assetId,
            tradeFilter
        )

    /**
     * Trades for an asset across multiple portfolios — used by the aggregated
     * holdings drill-down, where one asset is held in several portfolios.
     *
     * [PortfolioService.find] is called per id, so an unknown or unauthorized
     * portfolio rejects the whole request (fail-closed) rather than silently
     * returning a partial result. Each Trn carries its portfolio, so callers can
     * group the union by portfolio.
     */
    fun findAssetTrades(
        portfolioIds: List<String>,
        assetId: String
    ): Collection<Trn> =
        portfolioIds.flatMap { portfolioId ->
            findAssetTrades(
                portfolioService.find(portfolioId),
                assetId
            )
        }

    /**
     * Corporate actions
     *
     * @param portfolioId fully qualified portfolio the caller is authorized to view
     * @param assetId   filter by pk
     * @return Transactions in display order that is friendly for viewing.
     */
    fun findEvents(
        portfolioId: String,
        assetId: String
    ): Collection<Trn> =
        findEvents(
            portfolioService.find(portfolioId),
            assetId
        )

    fun findEvents(
        portfolio: Portfolio,
        assetId: String
    ): Collection<Trn> =
        trnResponse(
            portfolio,
            assetId,
            eventFilter
        )

    private fun trnResponse(
        portfolio: Portfolio,
        assetId: String,
        typeFilter: List<TrnType>
    ): Collection<Trn> {
        val results =
            trnRepository.findByPortfolioIdAndAssetIdAndTrnType(
                portfolio.id,
                assetId,
                typeFilter
            )
        log.trace(
            "Found {} for portfolio {} and asset {}",
            results.size,
            portfolio.code,
            assetId
        )
        return trnService.postProcess(
            results,
            true
        )
    }
}