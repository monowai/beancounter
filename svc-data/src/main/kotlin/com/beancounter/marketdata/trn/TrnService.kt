package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnDeleteResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.contracts.TrnStatusUpdateResponse
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.cash.CashAutoSettleService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.portfolio.PortfolioShareRepository
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.function.Consumer

/**
 * Core service for transaction CRUD operations and portfolio queries.
 *
 * For broker-related operations, see [TrnBrokerService].
 * For investment/income analysis, see [TrnAnalysisService].
 */
@Service
@Transactional
class TrnService(
    private val trnRepository: TrnRepository,
    private val trnInputMapper: TrnInputMapper,
    private val portfolioService: PortfolioService,
    private val trnMigrator: TrnMigrator,
    private val assetFinder: AssetFinder,
    private val systemUserService: SystemUserService,
    private val cacheInvalidationProducer: CacheInvalidationProducer,
    private val portfolioShareRepository: PortfolioShareRepository,
    private val cashAutoSettleService: CashAutoSettleService
) {
    private val log = LoggerFactory.getLogger(TrnService::class.java)

    fun getPortfolioTrn(trnId: String): Collection<Trn> {
        val trn = trnRepository.findById(trnId)
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
        return save(portfolio, trnRequest)
    }

    fun save(
        portfolio: Portfolio,
        trnRequest: TrnRequest
    ): Collection<Trn> {
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
        for (trn in results.toList()) {
            cashAutoSettleService.emitCompensatingTransfer(trn)
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
                TrnStatus.SETTLED
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
     * Find PROPOSED transactions for the current user.
     *
     * - OWNED: portfolios where current user is the owner (default historical behaviour).
     * - MANAGED: portfolios shared with the current user via an ACTIVE PortfolioShare.
     * - ALL: union of both.
     */
    fun findProposedForUser(scope: ProposedScope = ProposedScope.ALL): Collection<Trn> {
        val user = systemUserService.getOrThrow()
        val results = mutableMapOf<String, Trn>()

        if (scope == ProposedScope.OWNED || scope == ProposedScope.ALL) {
            trnRepository
                .findByStatusAndPortfolioOwner(TrnStatus.PROPOSED, user)
                .forEach { results[it.id] = it }
        }

        if (scope == ProposedScope.MANAGED || scope == ProposedScope.ALL) {
            val managedPortfolioIds = managedPortfolioIdsFor(user)
            if (managedPortfolioIds.isNotEmpty()) {
                trnRepository
                    .findByStatusAndPortfolioIdIn(TrnStatus.PROPOSED, managedPortfolioIds)
                    .forEach { results[it.id] = it }
            }
        }

        log.trace("proposed trns: ${results.size} (scope=$scope)")
        return postProcess(results.values.toList())
    }

    /**
     * Count PROPOSED transactions for the current user under the given scope.
     */
    fun countProposedForUser(scope: ProposedScope = ProposedScope.ALL): Long {
        val user = systemUserService.getOrThrow()
        var count = 0L

        if (scope == ProposedScope.OWNED || scope == ProposedScope.ALL) {
            count += trnRepository.countByStatusAndPortfolioOwner(TrnStatus.PROPOSED, user)
        }

        if (scope == ProposedScope.MANAGED || scope == ProposedScope.ALL) {
            val managedPortfolioIds = managedPortfolioIdsFor(user)
            if (managedPortfolioIds.isNotEmpty()) {
                count += trnRepository.countByStatusAndPortfolioIdIn(TrnStatus.PROPOSED, managedPortfolioIds)
            }
        }

        log.trace("proposed count: $count (scope=$scope)")
        return count
    }

    private fun managedPortfolioIdsFor(user: com.beancounter.common.model.SystemUser): Set<String> =
        portfolioShareRepository
            .findBySharedWithAndStatus(user, ShareStatus.ACTIVE)
            .mapNotNull { it.portfolio?.id }
            .toSet()

    /**
     * Find all SETTLED transactions for the current user on a specific trade date.
     */
    fun findSettledForUser(tradeDate: LocalDate): Collection<Trn> {
        val user = systemUserService.getOrThrow()
        val results = trnRepository.findByStatusAndPortfolioOwnerAndTradeDate(TrnStatus.SETTLED, user, tradeDate)
        log.trace("settled trns on $tradeDate: ${results.size}")
        return postProcess(results.toList())
    }

    /**
     * Get the Cash Ladder for a specific cash asset in a portfolio.
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
        val earliestSettled = settled.minOfOrNull { it.tradeDate }
        if (earliestSettled != null) {
            cacheInvalidationProducer.sendTransactionEvent(portfolio.id, earliestSettled)
        }
        return postProcess(settled)
    }

    /**
     * Find all transactions for a portfolio that belong to a specific rebalance model.
     */
    fun findByPortfolioAndModel(
        portfolioId: String,
        modelId: String
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        val results =
            trnRepository.findByPortfolioIdAndModelId(
                portfolio.id,
                modelId,
                TrnStatus.SETTLED
            )
        log.trace("trns: ${results.size}, portfolio: ${portfolio.code}, model: $modelId")
        return postProcess(results.toList())
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
        val parent =
            trnRepository.findById(trnId).orElseThrow {
                NotFoundException("Transaction not found: $trnId")
            }
        if (!portfolioService.canView(parent.portfolio)) {
            // Match unsettle / delete-existing behaviour — surface as 404
            // rather than a silent empty success.
            throw NotFoundException("Transaction not found: $trnId")
        }
        val siblings = cashAutoSettleService.findSiblings(parent).map { it.id }
        trnRepository.delete(parent)
        cacheInvalidationProducer.sendTransactionEvent(parent.portfolio.id, parent.tradeDate)
        return TrnDeleteResponse(listOf(parent.id), siblings)
    }

    /**
     * Toggle a trn from SETTLED to PROPOSED. Surfaces auto-settled siblings
     * for the caller to optionally delete via follow-up requests.
     *
     * Idempotency: callers must check status first. Calling unsettle on a trn
     * that isn't SETTLED throws — there's no useful "transition to PROPOSED
     * from PROPOSED" semantic.
     */
    fun unsettle(trnId: String): TrnStatusUpdateResponse {
        val parent =
            trnRepository.findById(trnId).orElseThrow {
                NotFoundException("Transaction not found: $trnId")
            }
        if (!portfolioService.canView(parent.portfolio)) {
            throw NotFoundException("Transaction not found: $trnId")
        }
        require(parent.status == TrnStatus.SETTLED) {
            "Cannot unsettle trn $trnId: status is ${parent.status}, expected SETTLED"
        }
        val siblings = cashAutoSettleService.findSiblings(parent).map { it.id }
        parent.status = TrnStatus.PROPOSED
        val saved = trnRepository.save(parent)
        cacheInvalidationProducer.sendTransactionEvent(parent.portfolio.id, parent.tradeDate)
        return TrnStatusUpdateResponse(updated = saved, siblings = siblings)
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
        val existing = getPortfolioTrn(trnId)
        val trn =
            trnInputMapper.map(
                portfolio,
                trnInput,
                existing.iterator().next()
            )
        trnRepository.save(trn)
        cacheInvalidationProducer.sendTransactionEvent(portfolio.id, trn.tradeDate)
        return TrnResponse(arrayListOf(trn))
    }

    private fun postProcess(trns: List<Trn>): List<Trn> {
        log.trace("PostProcess ${trns.size} transactions")
        // Asset hydration happens via AssetEntityListener @PostLoad — Trn.asset and
        // Trn.cashAsset arrive populated from JPA. Only the version-upgrade step here.
        for (trn in trns) {
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
                    portfolioService.isViewable(systemUser, it.portfolio)
                }
            return postProcess(filteredTrns)
        } else {
            return postProcess(trns.toList())
        }
    }
}