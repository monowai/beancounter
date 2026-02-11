package com.beancounter.common.contracts

import com.beancounter.common.model.PortfolioShare
import com.beancounter.common.model.ShareAccessLevel

/**
 * Request/response contracts for portfolio sharing.
 */
data class ShareInviteRequest(
    val portfolioIds: List<String>,
    val adviserEmail: String,
    val accessLevel: ShareAccessLevel = ShareAccessLevel.FULL
)

data class ShareRequestAccess(
    val clientEmail: String,
    val message: String? = null
)

data class PortfolioShareResponse(
    override var data: PortfolioShare
) : Payload<PortfolioShare>

data class PortfolioSharesResponse(
    override var data: Collection<PortfolioShare>
) : Payload<Collection<PortfolioShare>>

data class PendingSharesResponse(
    val invites: Collection<PortfolioShare>,
    val requests: Collection<PortfolioShare>
)