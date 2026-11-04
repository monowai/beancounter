package com.beancounter.marketdata.portfolio

import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Which portfolios hold an asset on a date: every holder for service callers
 * (corporate events), or only the holders the caller can view.
 */
@Service
@Transactional
class PortfolioHolders(
    private val portfolioRepository: PortfolioRepository,
    private val systemUserService: SystemUserService,
    private val dateUtils: DateUtils,
    private val accessControl: PortfolioAccessControl
) {
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

    private companion object {
        val log = LoggerFactory.getLogger(PortfolioHolders::class.java)
    }
}