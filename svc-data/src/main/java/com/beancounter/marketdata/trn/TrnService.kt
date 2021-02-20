package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.portfolio.PortfolioService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.function.Consumer
import javax.transaction.Transactional

@Service
@Transactional
class TrnService internal constructor(
    private val trnRepository: TrnRepository,
    private val trnAdapter: TrnAdapter,
    private val portfolioService: PortfolioService
) {

    fun getPortfolioTrn(portfolio: Portfolio, trnId: String): TrnResponse {
        val trn = trnRepository.findByPortfolioIdAndId(portfolio.id, trnId)
        val result = trn.map { transaction: Trn -> hydrate(setOf(transaction)) }
        if (result.isEmpty) {
            throw BusinessException(String.format("Trn %s not found", trnId))
        }
        return result.get()
    }

    fun save(portfolio: Portfolio, trnRequest: TrnRequest): TrnResponse {
        var trnResponse = trnAdapter.convert(portfolio, trnRequest)
        val (data) = findToCreate(trnResponse.data)
        // Figure out
        val saved = trnRepository.saveAll(data)
        val trns: MutableCollection<Trn> = ArrayList()
        saved.forEach(Consumer { e: Trn -> trns.add(e) })
        trnResponse = TrnResponse(trns)
        log.trace(
            "Wrote {}/{} transactions for {}",
            trnResponse.data.size,
            trnRequest.data.size,
            portfolio.code
        )
        return trnResponse
    }

    private fun findToCreate(data: Collection<Trn>): TrnResponse {
        val trns = ArrayList<Trn>()
        for (trn in data) {
            if (trnRepository.findByCallerRef(trn.callerRef!!).isEmpty) {
                trns.add(trn)
            }
        }
        return TrnResponse(trns)
    }

    /**
     * Display order.
     *
     * @param portfolio fully qualified portfolio the caller is authorised to view
     * @param assetId   filter by pk
     * @return Transactions in display order that is friendly for viewing.
     */
    fun findPortfolioAssetEvents(portfolio: Portfolio, assetId: String): TrnResponse {
        val typeFilter = ArrayList<TrnType>()
        typeFilter.add(TrnType.DIVI)
        typeFilter.add(TrnType.SPLIT)
        return trnResponse(portfolio, assetId, typeFilter)
    }

    fun findPortfolioAssetTrades(portfolio: Portfolio, assetId: String): TrnResponse {
        val typeFilter = ArrayList<TrnType>()
        typeFilter.add(TrnType.BUY)
        typeFilter.add(TrnType.SELL)
        return trnResponse(portfolio, assetId, typeFilter)
    }

    private fun trnResponse(portfolio: Portfolio, assetId: String, typeFilter: ArrayList<TrnType>): TrnResponse {
        val results = trnRepository
            .findByPortfolioIdAndAssetIdAndTrnType(
                portfolio.id,
                assetId,
                typeFilter,
                Sort.by("asset.code")
                    .and(Sort.by("tradeDate").descending())
            )
        log.debug(
            "Found {} for portfolio {} and asset {}",
            results.size,
            portfolio.code,
            assetId
        )
        return hydrate(results, true)
    }

    /**
     * Processing order.
     *
     * @param portfolio trusted
     * @param assetId   filter by pk
     * @param tradeDate until this date inclusive
     * @return transactions that can be accumulated into a position
     */
    fun findByPortfolioAsset(
        portfolio: Portfolio,
        assetId: String,
        tradeDate: LocalDate
    ): TrnResponse {
        val results = trnRepository
            .findByPortfolioIdAndAssetIdUpTo(
                portfolio.id,
                assetId,
                tradeDate
            )
        log.debug(
            "Found {} for portfolio {} and asset {}",
            results.size,
            portfolio.code,
            assetId
        )
        return hydrate(results, false)
    }

    fun findForPortfolio(portfolio: Portfolio, tradeDate: LocalDate): TrnResponse {
        val results = trnRepository.findByPortfolioId(
            portfolio.id,
            tradeDate,
            Sort.by("asset.code")
                .and(Sort.by("tradeDate"))
        )
        log.debug("trns: {}, portfolio: {}", results.size, portfolio.code)
        return hydrate(results)
    }

    /**
     * Purge transactions for a portfolio.
     *
     * @param portfolio portfolio owned by the caller
     * @return number of deleted transactions
     */
    fun purge(portfolio: Portfolio): Long {
        log.debug("Purging transactions for {}", portfolio.code)
        return trnRepository.deleteByPortfolioId(portfolio.id)
    }

    private fun setAssets(trn: Trn): Trn {
        trn.asset = trnAdapter.hydrate(trn.asset)!!
        trn.cashAsset = trnAdapter.hydrate(trn.cashAsset)
        return trn
    }

    private fun hydrate(trns: Iterable<Trn>, secure: Boolean = true): TrnResponse {
        val results: MutableCollection<Trn> = ArrayList()
        for (trn in trns) {
            val add = !secure || portfolioService.canView(trn.portfolio)
            if (add) {
                results.add(setAssets(trn))
            }
        }
        return if (results.isEmpty()) {
            TrnResponse() // Empty
        } else TrnResponse(results)
    }

    fun existing(trustedTrnEvent: TrustedTrnEvent): Collection<Trn> {
        val endDate = trustedTrnEvent.trnInput.tradeDate!!.plusDays(20)
        return trnRepository.findExisting(
            trustedTrnEvent.portfolio.id,
            trustedTrnEvent.trnInput.assetId,
            trustedTrnEvent.trnInput.trnType,
            trustedTrnEvent.trnInput.tradeDate!!,
            endDate
        )
    }

    fun delete(trnId: String): TrnResponse {
        val result = trnRepository.findById(trnId)
        if (result.isEmpty) {
            throw BusinessException(String.format("Transaction not found %s", trnId))
        }
        val trn = result.get()
        val deleted: MutableCollection<Trn> = ArrayList()
        if (portfolioService.canView(result.get().portfolio)) {
            trnRepository.delete(trn)
            deleted.add(trn)
        }
        return hydrate(deleted)
    }

    companion object {
        private val log = LoggerFactory.getLogger(TrnService::class.java)
    }
}
