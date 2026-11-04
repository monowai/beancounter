package com.beancounter.marketdata.trn

import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.portfolio.PortfolioShareRepository
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Read-only finders for transactions across the current user's portfolios: those they own and,
 * for proposed transactions, those shared with them. Portfolio-scoped finders live in
 * [TrnFinder].
 */
@Service
@Transactional
class UserTrnFinder(
    private val trnRepository: TrnRepository,
    private val systemUserService: SystemUserService,
    private val portfolioShareRepository: PortfolioShareRepository,
    private val dateUtils: DateUtils,
    private val trnPostProcessor: TrnPostProcessor
) {
    private val log = LoggerFactory.getLogger(UserTrnFinder::class.java)

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
     * Find all SETTLED transactions for the current user whose tradeDate falls within
     * the inclusive [from]..[to] window.
     */
    fun findSettledForUser(
        from: LocalDate,
        to: LocalDate
    ): Collection<Trn> {
        val user = systemUserService.getOrThrow()
        val results = trnRepository.findByStatusAndPortfolioOwnerAndTradeDateBetween(TrnStatus.SETTLED, user, from, to)
        log.trace("settled trns $from..$to: ${results.size}")
        return trnPostProcessor.postProcess(results.toList())
    }
}