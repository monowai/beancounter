package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.marketdata.portfolio.PortfolioService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.function.Consumer

/**
 * Interactions to created and read Trn objects.
 */
@Service
@Transactional
class TrnService internal constructor(
    private val trnRepository: TrnRepository,
    private val trnAdapter: TrnAdapter,
    private val portfolioService: PortfolioService,
    private val trnMigrator: TrnMigrator,
) {
    fun getPortfolioTrn(
        portfolio: Portfolio,
        trnId: String,
    ): TrnResponse {
        val trn = trnRepository.findByPortfolioIdAndId(portfolio.id, trnId)
        val result = trn.map { transaction: Trn -> postProcess(setOf(transaction)) }
        if (result.isEmpty) {
            throw BusinessException("Trn $trnId not found")
        }
        return result.get()
    }

    fun save(
        portfolio: Portfolio,
        trnRequest: TrnRequest,
    ): TrnResponse {
        // Figure out
        val saved = trnRepository.saveAll(trnAdapter.convert(portfolio, trnRequest).data)
        val results: MutableCollection<Trn> = mutableListOf()
        saved.forEach(Consumer { e: Trn -> results.add(e) })
        if (trnRequest.data.size == 1) {
            log.debug(
                "Wrote 1 transaction asset: ${trnRequest.data[0].assetId}, portfolio: ${portfolio.code}",
            )
        } else {
            log.debug(
                "Wrote ${results.size}/${trnRequest.data.size} transactions for ${portfolio.code}",
            )
        }
        return TrnResponse(results)
    }

    fun findForPortfolio(
        portfolio: Portfolio,
        tradeDate: LocalDate,
    ): TrnResponse {
        val results =
            trnRepository.findByPortfolioId(
                portfolio.id,
                tradeDate,
                Sort.by("tradeDate")
                    .and(Sort.by("asset.code")),
            )
        log.trace("trns: ${results.size}, portfolio: ${portfolio.code}, asAt: $tradeDate")
        return postProcess(results)
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

    private fun postProcess(trn: Trn): Trn {
        trn.asset = trnAdapter.hydrate(trn.asset)!!
        trn.cashAsset = trnAdapter.hydrate(trn.cashAsset)
        val upgraded = trnMigrator.upgrade(trn)
        if (upgraded.version != trn.version) {
            trnRepository.save(upgraded)
        }
        return upgraded
    }

    internal fun postProcess(
        trns: Iterable<Trn>,
        secure: Boolean = true,
    ): TrnResponse {
        val results: MutableCollection<Trn> = ArrayList()
        for (trn in trns) {
            val add = !secure || portfolioService.canView(trn.portfolio)
            if (add) {
                results.add(postProcess(trn))
            }
        }
        return if (results.isEmpty()) {
            TrnResponse() // Empty
        } else {
            TrnResponse(results)
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
            endDate,
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
        return postProcess(deleted)
    }

    fun patch(
        portfolio: Portfolio,
        trnId: String,
        trnInput: TrnInput,
    ): TrnResponse {
        val existing = getPortfolioTrn(portfolio, trnId)
        val trn = trnAdapter.map(portfolio, trnInput, existing.data.iterator().next())
        trnRepository.save(trn)
        return TrnResponse(arrayListOf(trn))
    }

    companion object {
        private val log = LoggerFactory.getLogger(TrnService::class.java)
    }
}
