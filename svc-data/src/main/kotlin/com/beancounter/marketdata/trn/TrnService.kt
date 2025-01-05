package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
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
class TrnService(
    private val trnRepository: TrnRepository,
    private val trnAdapter: TrnAdapter,
    private val portfolioService: PortfolioService,
    private val trnMigrator: TrnMigrator,
    private val assetService: AssetService,
    private val systemUserService: SystemUserService
) {
    fun getPortfolioTrn(
        // portfolio: Portfolio,
        trnId: String
    ): Collection<Trn> {
        val trn =
            trnRepository.findById(
                trnId
            )
        val result = trn.map { transaction: Trn -> postProcess(setOf(transaction)) }
        if (result.isEmpty) {
            throw BusinessException("Trn $trnId not found")
        }
        return result.get()
    }

    fun save(
        portfolio: Portfolio,
        trnRequest: TrnRequest
    ): Collection<Trn> {
        // Figure out
        val saved =
            trnRepository.saveAll(
                trnAdapter.convert(
                    portfolio,
                    trnRequest
                )
            )
        saved.forEach { trn ->
            trn.asset = assetService.hydrate(trn.asset)!!
            trn.cashAsset = trn.cashAsset?.let { assetService.hydrate(it) }
        }
        val results: MutableCollection<Trn> = mutableListOf()
        saved.forEach(Consumer { e: Trn -> results.add(e) })
        if (trnRequest.data.size == 1) {
            log.debug(
                "Wrote 1 transaction asset: ${trnRequest.data[0].assetId}, portfolio: ${portfolio.code}"
            )
        } else {
            log.debug(
                "Wrote ${results.size}/${trnRequest.data.size} transactions for ${portfolio.code}"
            )
        }
        return results
    }

    fun findForPortfolio(
        portfolio: Portfolio,
        tradeDate: LocalDate
    ): Collection<Trn> {
        val results =
            trnRepository.findByPortfolioId(
                portfolio.id,
                tradeDate,
                Sort.by("tradeDate").and(Sort.by("asset.code"))
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
    @Transactional
    fun purge(portfolio: Portfolio): Long {
        log.debug(
            "Purging transactions for {}",
            portfolio.code
        )
        return trnRepository.deleteByPortfolioId(portfolio.id)
    }

    private fun postProcess(trns: List<Trn>): List<Trn> {
        val assets =
            trns
                .flatMap {
                    listOfNotNull(
                        assetService.hydrate(it.asset),
                        assetService.hydrate(it.cashAsset)
                    )
                }.associateBy { it.id }

        for (trn in trns) {
            trn.asset = assets[trn.asset.id]!!
            trn.cashAsset = trn.cashAsset?.let { assets[it.id] }
            val upgraded = trnMigrator.upgrade(trn)
            if (upgraded.version != trn.version) {
                trnRepository.save(upgraded)
            }
        }
        return trns
    }

    internal fun postProcess(
        trns: Iterable<Trn>,
        secure: Boolean = true
    ): Collection<Trn> {
        if (secure) {
            val systemUser = systemUserService.getOrThrow
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

    fun delete(trnId: String): Collection<Trn> {
        val result = trnRepository.findById(trnId)
        if (result.isEmpty) {
            throw BusinessException(
                String.format(
                    "Transaction not found %s",
                    trnId
                )
            )
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
        trnInput: TrnInput
    ): TrnResponse {
        val existing =
            getPortfolioTrn(
                // portfolio,
                trnId
            )
        val trn =
            trnAdapter.map(
                portfolio,
                trnInput,
                existing.iterator().next()
            )
        trnRepository.save(trn)
        return TrnResponse(arrayListOf(trn))
    }

    companion object {
        private val log = LoggerFactory.getLogger(TrnService::class.java)
    }
}