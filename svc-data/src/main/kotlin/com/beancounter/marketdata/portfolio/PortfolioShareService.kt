package com.beancounter.marketdata.portfolio

import com.beancounter.common.contracts.PendingSharesResponse
import com.beancounter.common.contracts.ShareInviteRequest
import com.beancounter.common.contracts.ShareRequestAccess
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.PortfolioShare
import com.beancounter.common.model.ShareAccessLevel
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.share.ShareLifecycle
import com.beancounter.marketdata.share.maskUnlessCaller
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

/**
 * Business logic for portfolio sharing between clients and advisers.
 */
@Service
@Transactional
class PortfolioShareService(
    private val portfolioShareRepository: PortfolioShareRepository,
    private val portfolioService: PortfolioService,
    private val systemUserService: SystemUserService
) {
    private val shares = ShareLifecycle(portfolioShareRepository, systemUserService)

    /**
     * Client invites an adviser to view/manage selected portfolios.
     */
    fun inviteAdviser(request: ShareInviteRequest): Collection<PortfolioShare> {
        val client = systemUserService.getOrThrow()
        val adviser = shares.counterparty(request.adviserEmail, client, "Cannot share portfolios with yourself")

        return request.portfolioIds.map { portfolioId ->
            val portfolio = portfolioService.find(portfolioId)
            validateOwnership(portfolio, client)
            shares.requireNotShared(
                portfolioShareRepository.findByPortfolioAndSharedWith(portfolio, adviser),
                "Portfolio ${portfolio.code} is already shared with this user"
            )

            portfolioShareRepository.save(
                PortfolioShare(
                    portfolio = portfolio,
                    sharedWith = adviser,
                    accessLevel = request.accessLevel,
                    status = ShareStatus.PENDING_CLIENT_INVITE,
                    createdBy = client
                )
            )
        }
    }

    private fun validateOwnership(
        portfolio: Portfolio,
        client: SystemUser
    ) {
        if (portfolio.owner.id != client.id) {
            throw BusinessException("You can only share portfolios you own")
        }
    }

    /**
     * Adviser requests access from a client. No portfolio is specified;
     * the client will later choose which portfolios to share via the invite flow.
     */
    fun requestAccess(request: ShareRequestAccess): PortfolioShare {
        val adviser = systemUserService.getOrThrow()
        val client = shares.counterparty(request.clientEmail, adviser, "Cannot request access to your own portfolios")

        val existingRequests =
            portfolioShareRepository.findByTargetUserAndStatus(
                client,
                ShareStatus.PENDING_ADVISER_REQUEST
            )
        if (existingRequests.any { it.sharedWith.id == adviser.id }) {
            throw BusinessException("You already have a pending request with this client")
        }

        return portfolioShareRepository.save(
            PortfolioShare(
                sharedWith = adviser,
                accessLevel = ShareAccessLevel.FULL,
                status = ShareStatus.PENDING_ADVISER_REQUEST,
                createdBy = adviser,
                targetUser = client
            )
        )
    }

    /**
     * Accept a pending invitation or request.
     */
    fun acceptShare(shareId: String): PortfolioShare = shares.accept(shareId)

    /**
     * Either party can revoke a share.
     */
    fun revokeShare(shareId: String): PortfolioShare = shares.revoke(shareId)

    /**
     * Get pending notifications for the current user:
     * - invites: portfolio shares pending my acceptance as adviser
     * - requests: adviser access requests targeting me as client
     */
    fun getPendingNotifications(): PendingSharesResponse {
        val currentUser = systemUserService.getOrThrow()

        val invites =
            portfolioShareRepository
                .findBySharedWithAndStatusIn(
                    currentUser,
                    listOf(ShareStatus.PENDING_CLIENT_INVITE)
                ).toList()
                .map { maskShareEmails(it, currentUser) }

        val incomingRequests =
            portfolioShareRepository
                .findByTargetUserAndStatus(
                    currentUser,
                    ShareStatus.PENDING_ADVISER_REQUEST
                ).toList()
                .map { maskShareEmails(it, currentUser) }

        return PendingSharesResponse(
            invites = invites,
            requests = incomingRequests
        )
    }

    /**
     * Get active managed portfolios for the current adviser.
     * Owner emails are masked.
     */
    fun getManagedPortfolios(): Collection<PortfolioShare> {
        val adviser = systemUserService.getOrThrow()
        return portfolioShareRepository
            .findBySharedWithAndStatus(
                adviser,
                ShareStatus.ACTIVE
            ).toList()
            .filter { it.portfolio != null }
            .map { maskShareEmails(it, adviser) }
    }

    /**
     * Get shares for a specific portfolio (owner view).
     *
     * Owner-only: a viewer with an accepted share can `find()` the
     * portfolio via `canView`, but listing every other user the owner
     * shared with leaks the owner's adviser/collaborator graph. Gate
     * here rather than at `find()` since `find()` is the shared
     * cross-service viewability check.
     */
    fun getPortfolioShares(portfolioId: String): Collection<PortfolioShare> {
        val caller = systemUserService.getOrThrow()
        val portfolio = portfolioService.find(portfolioId)
        if (portfolio.owner.id != caller.id) {
            throw ForbiddenException("Only the portfolio owner can list its shares")
        }
        return portfolioShareRepository
            .findByPortfolioAndStatusNot(
                portfolio,
                ShareStatus.REVOKED
            ).toList()
    }

    /**
     * Outbound shares the caller has granted across ALL their portfolios.
     * Excludes REVOKED. Used by the central "Shares I've granted" surface
     * so the owner can review + revoke without drilling into each
     * portfolio individually.
     */
    fun getGrantedShares(): Collection<PortfolioShare> {
        val owner = systemUserService.getOrThrow()
        return portfolioShareRepository
            .findByPortfolioOwnerAndStatusNot(
                owner,
                ShareStatus.REVOKED
            ).toList()
            .filter { it.portfolio != null }
    }

    private fun maskShareEmails(
        share: PortfolioShare,
        currentUser: SystemUser
    ): PortfolioShare =
        share.copy(
            portfolio = share.portfolio?.let { it.copy(owner = maskUnlessCaller(it.owner, currentUser)) },
            sharedWith = maskUnlessCaller(share.sharedWith, currentUser),
            createdBy = maskUnlessCaller(share.createdBy, currentUser)
        )
}