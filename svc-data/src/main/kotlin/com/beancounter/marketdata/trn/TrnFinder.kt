package com.beancounter.marketdata.trn

import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.portfolio.PortfolioShareRepository
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Read-only finders for transactions scoped to a portfolio, status, model, cash
 * asset, or the current user. Mutations and lifecycle live in [TrnService] and
 * [TrnSettlementService]; asset/event queries live in [TrnQueryService].
 */
@Service
@Transactional
class TrnFinder(
    private val trnRepository: TrnRepository,
    private val portfolioService: PortfolioService,
    private val systemUserService: SystemUserService,
    private val portfolioShareRepository: PortfolioShareRepository,
    private val dateUtils: DateUtils,
    private val trnPostProcessor: TrnPostProcessor
) {
    private val log = LoggerFactory.getLogger(TrnFinder::class.java)

    fun getPortfolioTrn(trnId: String): Collection<Trn> {
        val trn = trnRepository.findById(trnId)
        if (trn.isEmpty) {
            throw NotFoundException("Trn not found: $trnId")
        }
        val result = trn.map { transaction: Trn -> trnPostProcessor.postProcess(setOf(transaction)) }
        return result.get()
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
        return trnPostProcessor.postProcess(results)
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
        return trnPostProcessor.postProcess(results)
    }

    /**
     * Find PROPOSED transactions for the current user, bounded to those due on or before [asAt]
     * (defaults to today). Future-dated proposed transactions — e.g. dividends with a forward pay
     * date — are excluded until [asAt] reaches their tradeDate, so the review list only shows
     * actionable rows. Widen [asAt] to preview upcoming proposed transactions.
     *
     * - OWNED: portfolios where current user is the owner (default historical behaviour).
     * - MANAGED: portfolios shared with the current user via an ACTIVE PortfolioShare.
     * - ALL: union of both.
     */
    fun findProposedForUser(
        scope: ProposedScope = ProposedScope.ALL,
        asAt: LocalDate = dateUtils.date
    ): Collection<Trn> {
        val user = systemUserService.getOrThrow()
        val results = mutableMapOf<String, Trn>()

        if (scope == ProposedScope.OWNED || scope == ProposedScope.ALL) {
            trnRepository
                .findByStatusAndPortfolioOwner(TrnStatus.PROPOSED, user, asAt)
                .forEach { results[it.id] = it }
        }

        if (scope == ProposedScope.MANAGED || scope == ProposedScope.ALL) {
            val managedPortfolioIds = managedPortfolioIdsFor(user)
            if (managedPortfolioIds.isNotEmpty()) {
                trnRepository
                    .findByStatusAndPortfolioIdIn(TrnStatus.PROPOSED, managedPortfolioIds, asAt)
                    .forEach { results[it.id] = it }
            }
        }

        log.trace("proposed trns: ${results.size} (scope=$scope, asAt=$asAt)")
        return trnPostProcessor.postProcess(results.values.toList())
    }

    /**
     * Count PROPOSED transactions for the current user under the given scope, bounded to those due
     * on or before [asAt] (defaults to today) so the badge tracks the same set as the review list.
     */
    fun countProposedForUser(
        scope: ProposedScope = ProposedScope.ALL,
        asAt: LocalDate = dateUtils.date
    ): Long {
        val user = systemUserService.getOrThrow()
        var count = 0L

        if (scope == ProposedScope.OWNED || scope == ProposedScope.ALL) {
            count += trnRepository.countByStatusAndPortfolioOwner(TrnStatus.PROPOSED, user, asAt)
        }

        if (scope == ProposedScope.MANAGED || scope == ProposedScope.ALL) {
            val managedPortfolioIds = managedPortfolioIdsFor(user)
            if (managedPortfolioIds.isNotEmpty()) {
                count += trnRepository.countByStatusAndPortfolioIdIn(TrnStatus.PROPOSED, managedPortfolioIds, asAt)
            }
        }

        log.trace("proposed count: $count (scope=$scope, asAt=$asAt)")
        return count
    }

    private fun managedPortfolioIdsFor(user: SystemUser): Set<String> =
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
        return trnPostProcessor.postProcess(results.toList())
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
        return trnPostProcessor.postProcess(results.toList())
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
        return trnPostProcessor.postProcess(results.toList())
    }
}