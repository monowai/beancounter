package com.beancounter.marketdata.trn

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Queries that return collections of transactions
 */
@Service
@Transactional
class TrnQueryService(
    val trnService: TrnService,
    val trnRepository: TrnRepository,
) {
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
        tradeDate: LocalDate,
    ): Collection<Trn> {
        val results =
            trnRepository
                .findByPortfolioIdAndAssetIdUpTo(
                    portfolio.id,
                    assetId,
                    tradeDate,
                )
        log.debug(
            "count: {}, portfolio: {}, asset: {}",
            results.size,
            portfolio.code,
            assetId,
        )
        return trnService.postProcess(
            results,
            false,
        )
    }

    private val typeFilter =
        arrayListOf(
            TrnType.BUY,
            TrnType.SELL,
            TrnType.SPLIT,
            TrnType.DEPOSIT,
            TrnType.WITHDRAWAL,
            TrnType.FX_BUY,
            TrnType.BALANCE,
            TrnType.ADD,
        )

    fun findAssetTrades(
        portfolio: Portfolio,
        assetId: String,
    ): Collection<Trn> =
        trnResponse(
            portfolio,
            assetId,
            typeFilter,
        )

    /**
     * Corporate actions
     *
     * @param portfolio fully qualified portfolio the caller is authorised to view
     * @param assetId   filter by pk
     * @return Transactions in display order that is friendly for viewing.
     */
    fun findEvents(
        portfolio: Portfolio,
        assetId: String,
    ): Collection<Trn> {
        val typeFilter = ArrayList<TrnType>()
        typeFilter.add(TrnType.DIVI)
        typeFilter.add(TrnType.SPLIT)
        return trnResponse(
            portfolio,
            assetId,
            typeFilter,
        )
    }

    private fun trnResponse(
        portfolio: Portfolio,
        assetId: String,
        typeFilter: ArrayList<TrnType>,
    ): Collection<Trn> {
        val results =
            trnRepository
                .findByPortfolioIdAndAssetIdAndTrnType(
                    portfolio.id,
                    assetId,
                    typeFilter,
                    Sort
                        .by("tradeDate")
                        .descending()
                        .and(Sort.by("asset.code")),
                )
        log.debug(
            "Found {} for portfolio {} and asset {}",
            results.size,
            portfolio.code,
            assetId,
        )
        return trnService.postProcess(
            results,
            true,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(TrnQueryService::class.java)
    }
}
