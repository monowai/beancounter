package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnDeleteResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.contracts.TrnSaveResult
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.cash.CashAutoSettleService
import com.beancounter.marketdata.portfolio.PortfolioAccessControl
import com.beancounter.marketdata.portfolio.PortfolioService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.function.Consumer

/**
 * Core service for transaction writes (create / patch / delete / purge).
 *
 * Reads live in [TrnFinder] (portfolio-scoped) and [TrnQueryService] (asset /
 * event). Settle / unsettle lifecycle lives in [TrnSettlementService]. Shared
 * read-time processing lives in [TrnPostProcessor]. Broker operations live in
 * [TrnBrokerService]; investment/income analysis in [TrnAnalysisService].
 */
@Service
@Transactional
class TrnService(
    private val trnRepository: TrnRepository,
    private val trnInputMapper: TrnInputMapper,
    private val portfolioService: PortfolioService,
    private val portfolioAccessControl: PortfolioAccessControl,
    private val cacheInvalidationProducer: CacheInvalidationProducer,
    private val cashAutoSettleService: CashAutoSettleService,
    private val trnFinder: TrnFinder
) {
    private val log = LoggerFactory.getLogger(TrnService::class.java)

    fun save(
        portfolioId: String,
        trnRequest: TrnRequest
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        return save(portfolio, trnRequest)
    }

    fun save(
        portfolio: Portfolio,
        trnRequest: TrnRequest
    ): Collection<Trn> = saveWithResult(portfolio, trnRequest).trns

    fun saveWithResult(
        portfolioId: String,
        trnRequest: TrnRequest
    ): TrnSaveResult = saveWithResult(portfolioService.find(portfolioId), trnRequest)

    /**
     * Persist trns and return them along with any non-fatal warnings raised
     * during auto-settle (e.g. cash funding portfolio has no balance in the
     * trade currency). Controllers wrap the warnings into [TrnResponse] so
     * the UI can surface them; legacy callers (CashTransferService etc.)
     * use the [save] overload that only returns the trns.
     */
    fun saveWithResult(
        portfolio: Portfolio,
        trnRequest: TrnRequest
    ): TrnSaveResult {
        val saved =
            trnRepository.saveAll(
                trnInputMapper.convert(portfolio, trnRequest)
            )
        // Asset hydration happens via AssetEntityListener @PostLoad on every load —
        // saveAll returns entities whose asset reference was already hydrated upstream.
        val results: MutableCollection<Trn> = mutableListOf()
        saved.forEach(Consumer { e: Trn -> results.add(e) })
        // Auto-settle cash to the linked funding portfolio. Skips non-trigger
        // types (DEPOSIT/WITHDRAWAL etc.) — no recursion risk.
        val warnings = mutableListOf<String>()
        for (trn in results.toList()) {
            // Cash auto-settle emits a compensating pair whose status mirrors the
            // parent — a PROPOSED trade carries PROPOSED legs (no settled cash
            // impact), a SETTLED trade carries SETTLED legs. Settling later
            // reconciles them to SETTLED. emitCompensatingTransfer no-ops for
            // non-trigger types (DEPOSIT/WITHDRAWAL etc.), so no recursion.
            val res = cashAutoSettleService.emitCompensatingTransfer(trn)
            warnings.addAll(res.warnings)
        }
        if (trnRequest.data.size == 1) {
            log.trace(
                "Wrote 1 transaction asset: ${trnRequest.data[0].assetId}, portfolio: ${portfolio.code}"
            )
        } else {
            log.trace(
                "Wrote ${results.size}/${trnRequest.data.size} transactions for ${portfolio.code}"
            )
        }
        val earliestDate = results.minOfOrNull { it.tradeDate }
        if (earliestDate != null) {
            cacheInvalidationProducer.sendTransactionEvent(portfolio.id, earliestDate)
        }
        return TrnSaveResult(results.toList(), warnings.toList())
    }

    fun purge(portfolioId: String): Long {
        val portfolio = portfolioService.find(portfolioId)
        return purge(portfolio)
    }

    /**
     * Purge transactions for a portfolio.
     */
    fun purge(portfolio: Portfolio): Long {
        log.debug("Purging transactions for {}", portfolio.code)
        val count = trnRepository.deleteByPortfolioId(portfolio.id)
        cacheInvalidationProducer.sendTransactionEvent(portfolio.id, LocalDate.MIN)
        return count
    }

    fun delete(trnId: String): Collection<String> {
        val result = trnRepository.getOrThrow(trnId)
        val deleted = mutableListOf<Trn>()
        if (portfolioAccessControl.canView(result.portfolio)) {
            trnRepository.delete(result)
            deleted.add(result)
            cacheInvalidationProducer.sendTransactionEvent(result.portfolio.id, result.tradeDate)
        }
        return deleted.map { it.id }
    }

    /**
     * Delete one trn and surface its auto-settled siblings (without cascading).
     * The UI uses [TrnDeleteResponse.siblings] to prompt the user before
     * issuing follow-up DELETEs for the W+D cash legs.
     */
    fun deleteWithSiblings(trnId: String): TrnDeleteResponse {
        val parent = trnRepository.getOrThrow(trnId)
        if (!portfolioAccessControl.canView(parent.portfolio)) {
            // Match unsettle / delete-existing behaviour — surface as 404
            // rather than a silent empty success.
            throw NotFoundException("Transaction not found: $trnId")
        }
        val siblings = cashAutoSettleService.findSiblings(parent).map { it.id }
        trnRepository.delete(parent)
        cacheInvalidationProducer.sendTransactionEvent(parent.portfolio.id, parent.tradeDate)
        return TrnDeleteResponse(listOf(parent.id), siblings)
    }

    fun patch(
        portfolioId: String,
        trnId: String,
        trnInput: TrnInput
    ): TrnResponse {
        val portfolio = portfolioService.find(portfolioId)
        return patch(portfolio, trnId, trnInput)
    }

    fun patch(
        portfolio: Portfolio,
        trnId: String,
        trnInput: TrnInput
    ): TrnResponse {
        val existing = trnFinder.getPortfolioTrn(trnId)
        val trn =
            trnInputMapper.map(
                portfolio,
                trnInput,
                existing.iterator().next()
            )
        trnRepository.save(trn)
        // Re-sync the compensating cash transfer to the edited values — reconcile
        // deletes any stale pair and re-posts so the legs track the new
        // amount/date/currency AND the new status. Editing a SETTLED trade down to
        // PROPOSED must revert its legs to PROPOSED too (not orphan them settled).
        val warnings = cashAutoSettleService.emitCompensatingTransfer(trn).warnings
        cacheInvalidationProducer.sendTransactionEvent(portfolio.id, trn.tradeDate)
        return TrnResponse(arrayListOf(trn), warnings)
    }
}