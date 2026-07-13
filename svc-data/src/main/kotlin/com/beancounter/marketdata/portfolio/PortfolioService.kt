package com.beancounter.marketdata.portfolio

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.Locale
import java.util.function.Consumer

/**
 * Server side portfolio activities.
 */
@Service
@Transactional
class PortfolioService(
    private val portfolioInputAdapter: PortfolioInputAdapter,
    private val portfolioRepository: PortfolioRepository,
    private val trnRepository: TrnRepository,
    private val systemUserService: SystemUserService,
    private val dateUtils: DateUtils,
    private val accessControl: PortfolioAccessControl
) {
    fun save(portfolios: Collection<PortfolioInput>): Collection<Portfolio> {
        val owner = systemUserService.getOrThrow()
        val results: MutableCollection<Portfolio> = ArrayList()
        portfolioRepository
            .saveAll(
                portfolioInputAdapter.prepare(
                    owner,
                    portfolios
                )
            ).forEach(Consumer { e: Portfolio -> results.add(e) })
        return results
    }

    /**
     * Returns portfolios for the current user, or — when [systemUserId] is
     * supplied AND the caller is a service account — for that specific
     * [com.beancounter.common.model.SystemUser]. The owner-scoped path is
     * used by svc-retire to resolve a shared plan's holdings without
     * leaking the viewer's data.
     *
     * @param includeInactive if true, includes inactive portfolios
     * @param systemUserId target owner's SystemUser.id; service-account only.
     *   Non-service callers receive a [ForbiddenException].
     */
    fun portfolios(
        includeInactive: Boolean = false,
        systemUserId: String? = null
    ): Collection<Portfolio> {
        val systemUser =
            if (systemUserId != null) {
                if (!systemUserService.isServiceAccount()) {
                    throw ForbiddenException("systemUserId parameter requires service authentication")
                }
                systemUserService.findById(systemUserId) ?: return emptyList()
            } else {
                systemUserService.getOrThrow()
            }
        val portfolios =
            if (systemUser.id == AuthConstants.SYSTEM) {
                if (includeInactive) {
                    portfolioRepository.findAll().toList()
                } else {
                    portfolioRepository.findByActive(true).toList()
                }
            } else {
                if (includeInactive) {
                    portfolioRepository.findByOwner(systemUser).toList()
                } else {
                    portfolioRepository.findByOwnerAndActive(systemUser, true).toList()
                }
            }
        return portfolios
    }

    /**
     * Returns all portfolios in the system. Used by scheduled jobs.
     * By default, only active portfolios are returned.
     *
     * @param includeInactive if true, includes inactive portfolios
     */
    fun findAll(includeInactive: Boolean = false): Collection<Portfolio> =
        if (includeInactive) {
            portfolioRepository.findAll().toList()
        } else {
            portfolioRepository.findByActive(true).toList()
        }

    /**
     * Confirms if the requested portfolio is known. Service side call.
     *
     * @param id pk
     * @return exists or not
     */
    fun verify(id: String): Boolean {
        val found = portfolioRepository.findById(id)
        return found.isPresent
    }

    fun find(id: String): Portfolio {
        val found = portfolioRepository.findById(id)
        val portfolio =
            found.orElseThrow {
                NotFoundException("Portfolio not found: $id")
            }
        if (accessControl.canView(portfolio)) {
            return portfolio
        }
        throw NotFoundException("Portfolio not found: $id")
    }

    // Unauthenticated lookup for system-internal callers (stream consumers).
    // Skips canView — no JWT in scope on the stream thread.
    fun findOrNull(id: String): Portfolio? = portfolioRepository.findById(id).orElse(null)

    fun findByCode(code: String): Portfolio {
        val systemUser = systemUserService.getOrThrow()
        log.trace(
            "Searching on behalf of {}",
            systemUser.id
        )
        val found =
            portfolioRepository.findByCodeAndOwner(
                code.uppercase(Locale.getDefault()),
                systemUser
            )
        val portfolio =
            found.orElseThrow {
                NotFoundException("Portfolio not found: $code")
            }
        if (accessControl.canView(portfolio)) {
            return portfolio
        }
        throw NotFoundException("Portfolio not found: $code")
    }

    fun update(
        id: String,
        portfolioInput: PortfolioInput?
    ): Portfolio {
        val existing = find(id)
        return portfolioRepository.save(
            portfolioInputAdapter.fromInput(
                portfolioInput!!,
                existing
            )
        )
    }

    fun maintain(portfolio: Portfolio): Portfolio = portfolioRepository.save(portfolio)

    fun delete(id: String) {
        val portfolio = find(id)
        trnRepository.deleteByPortfolioId(portfolio.id)
        portfolioRepository.delete(portfolio)
    }

    fun findWhereHeld(
        assetId: String?,
        tradeDate: LocalDate?
    ): PortfoliosResponse {
        val recordDate = tradeDate ?: dateUtils.getFormattedDate(dateUtils.today())
        val portfolios =
            portfolioRepository.findDistinctPortfolioByAssetIdAndTradeDate(
                assetId!!,
                recordDate
            )
        log.trace(
            "Found {} notional holders for assetId: {}",
            portfolios.size,
            assetId
        )
        return PortfoliosResponse(portfolios)
    }

    /**
     * Same as [findWhereHeld] but restricted to portfolios the current caller
     * can view (owner or active share). Use this for user-scoped lookups
     * (e.g. Asset Lookup); service-to-service callers should use [findWhereHeld].
     */
    fun findWhereHeldForCaller(
        assetId: String?,
        tradeDate: LocalDate?
    ): PortfoliosResponse {
        val all = findWhereHeld(assetId, tradeDate).data
        val systemUser = systemUserService.getOrThrow()
        val viewable = all.filter { accessControl.isViewable(systemUser, it) }
        log.trace(
            "Filtered {} -> {} viewable holders for assetId: {} on behalf of {}",
            all.size,
            viewable.size,
            assetId,
            systemUser.id
        )
        return PortfoliosResponse(viewable)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioService::class.java)
    }
}