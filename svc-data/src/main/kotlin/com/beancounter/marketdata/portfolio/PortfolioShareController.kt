package com.beancounter.marketdata.portfolio

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PendingSharesResponse
import com.beancounter.common.contracts.PortfolioShareResponse
import com.beancounter.common.contracts.PortfolioSharesResponse
import com.beancounter.common.contracts.ShareInviteRequest
import com.beancounter.common.contracts.ShareRequestAccess
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/shares")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Portfolio Sharing",
    description = "Operations for sharing portfolios between clients and advisers"
)
class PortfolioShareController internal constructor(
    private val portfolioShareService: PortfolioShareService
) {
    @PostMapping("/invite")
    @Operation(summary = "Invite an adviser to view selected portfolios")
    fun inviteAdviser(
        @RequestBody request: ShareInviteRequest
    ): PortfolioSharesResponse = PortfolioSharesResponse(portfolioShareService.inviteAdviser(request))

    @PostMapping("/request")
    @Operation(summary = "Request access to a client's portfolios")
    fun requestAccess(
        @RequestBody request: ShareRequestAccess
    ): PortfolioShareResponse = PortfolioShareResponse(portfolioShareService.requestAccess(request))

    @PostMapping("/{shareId}/accept")
    @Operation(summary = "Accept a pending share invitation or request")
    fun acceptShare(
        @PathVariable shareId: String
    ): PortfolioShareResponse = PortfolioShareResponse(portfolioShareService.acceptShare(shareId))

    @DeleteMapping("/{shareId}")
    @Operation(summary = "Revoke an active or pending share")
    fun revokeShare(
        @PathVariable shareId: String
    ): PortfolioShareResponse = PortfolioShareResponse(portfolioShareService.revokeShare(shareId))

    @GetMapping("/pending")
    @Operation(summary = "Get pending invitations and requests for the current user")
    fun getPendingNotifications(): PendingSharesResponse = portfolioShareService.getPendingNotifications()

    @GetMapping("/managed")
    @Operation(summary = "Get portfolios shared with the current user (adviser view)")
    fun getManagedPortfolios(): PortfolioSharesResponse =
        PortfolioSharesResponse(portfolioShareService.getManagedPortfolios())

    @GetMapping("/portfolio/{portfolioId}")
    @Operation(summary = "Get shares for a specific portfolio (owner view)")
    fun getPortfolioShares(
        @PathVariable portfolioId: String
    ): PortfolioSharesResponse = PortfolioSharesResponse(portfolioShareService.getPortfolioShares(portfolioId))
}