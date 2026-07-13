package com.beancounter.marketdata.portfolio

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.registration.SystemUserService
import org.springframework.stereotype.Service

/**
 * Access-control checks for portfolios.
 *
 * A portfolio is viewable by the SYSTEM account, its owner, or any user
 * holding an ACTIVE [com.beancounter.common.model.PortfolioShare] over it.
 * Write paths remain owner-scoped via the owning services.
 */
@Service
class PortfolioAccessControl(
    private val systemUserService: SystemUserService,
    private val portfolioShareRepository: PortfolioShareRepository
) {
    fun canView(portfolio: Portfolio): Boolean =
        isViewable(
            systemUserService.getOrThrow(),
            portfolio
        )

    fun isViewable(
        systemUser: SystemUser,
        portfolio: Portfolio
    ): Boolean {
        if (systemUser.id == AuthConstants.SYSTEM || portfolio.owner.id == systemUser.id) {
            return true
        }
        val share = portfolioShareRepository.findByPortfolioAndSharedWith(portfolio, systemUser)
        return share.isPresent && share.get().status == ShareStatus.ACTIVE
    }
}