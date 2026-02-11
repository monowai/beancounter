package com.beancounter.marketdata.portfolio

import com.beancounter.common.contracts.PendingSharesResponse
import com.beancounter.common.contracts.ShareInviteRequest
import com.beancounter.common.contracts.ShareRequestAccess
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.PortfolioShare
import com.beancounter.common.model.ShareAccessLevel
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

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
    /**
     * Client invites an adviser to view/manage selected portfolios.
     */
    fun inviteAdviser(request: ShareInviteRequest): Collection<PortfolioShare> {
        val client = systemUserService.getOrThrow()
        val adviser = resolveUserByEmail(request.adviserEmail)
        if (adviser.id == client.id) {
            throw BusinessException("Cannot share portfolios with yourself")
        }

        return request.portfolioIds.map { portfolioId ->
            val portfolio = portfolioService.find(portfolioId)
            if (portfolio.owner.id != client.id) {
                throw BusinessException("You can only share portfolios you own")
            }

            val existing = portfolioShareRepository.findByPortfolioAndSharedWith(portfolio, adviser)
            if (existing.isPresent) {
                val share = existing.get()
                if (share.status == ShareStatus.ACTIVE || share.status == ShareStatus.PENDING_CLIENT_INVITE) {
                    throw BusinessException("Portfolio ${portfolio.code} is already shared with this user")
                }
            }

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

    /**
     * Adviser requests access from a client. No portfolio is specified;
     * the client will later choose which portfolios to share via the invite flow.
     */
    fun requestAccess(request: ShareRequestAccess): PortfolioShare {
        val adviser = systemUserService.getOrThrow()
        val client = resolveUserByEmail(request.clientEmail)
        if (client.id == adviser.id) {
            throw BusinessException("Cannot request access to your own portfolios")
        }

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
    fun acceptShare(shareId: String): PortfolioShare {
        val currentUser = systemUserService.getOrThrow()
        val share = findShareOrThrow(shareId)

        when (share.status) {
            ShareStatus.PENDING_CLIENT_INVITE -> {
                if (share.sharedWith.id != currentUser.id) {
                    throw NotFoundException("Share not found: $shareId")
                }
            }
            ShareStatus.PENDING_ADVISER_REQUEST -> {
                if (share.targetUser?.id != currentUser.id) {
                    throw NotFoundException("Share not found: $shareId")
                }
            }
            else -> {
                throw BusinessException("Share is not pending: $shareId")
            }
        }

        share.status = ShareStatus.ACTIVE
        share.acceptedAt = Instant.now()
        return portfolioShareRepository.save(share)
    }

    /**
     * Either party can revoke a share.
     */
    fun revokeShare(shareId: String): PortfolioShare {
        val currentUser = systemUserService.getOrThrow()
        val share = findShareOrThrow(shareId)

        val isOwner = share.portfolio?.owner?.id == currentUser.id
        val isAdviser = share.sharedWith.id == currentUser.id
        val isTarget = share.targetUser?.id == currentUser.id
        if (!isOwner && !isAdviser && !isTarget) {
            throw NotFoundException("Share not found: $shareId")
        }

        share.status = ShareStatus.REVOKED
        return portfolioShareRepository.save(share)
    }

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
     */
    fun getPortfolioShares(portfolioId: String): Collection<PortfolioShare> {
        val portfolio = portfolioService.find(portfolioId)
        return portfolioShareRepository
            .findByPortfolioAndStatusNot(
                portfolio,
                ShareStatus.REVOKED
            ).toList()
    }

    /**
     * Check if a user has an active share for a portfolio.
     */
    fun hasActiveShare(
        portfolio: Portfolio,
        user: SystemUser
    ): Boolean {
        val share = portfolioShareRepository.findByPortfolioAndSharedWith(portfolio, user)
        return share.isPresent && share.get().status == ShareStatus.ACTIVE
    }

    private fun findShareOrThrow(shareId: String): PortfolioShare =
        portfolioShareRepository.findById(shareId).orElseThrow {
            NotFoundException("Share not found: $shareId")
        }

    private fun resolveUserByEmail(email: String): SystemUser =
        systemUserService.findByEmail(email)
            ?: throw NotFoundException("User not found: ${maskEmail(email)}")

    private fun maskShareEmails(
        share: PortfolioShare,
        currentUser: SystemUser
    ): PortfolioShare {
        val maskedOwner =
            share.portfolio?.owner?.let { owner ->
                if (owner.id != currentUser.id) {
                    owner.copy(email = maskEmail(owner.email))
                } else {
                    owner
                }
            }

        val maskedSharedWith =
            if (share.sharedWith.id != currentUser.id) {
                share.sharedWith.copy(email = maskEmail(share.sharedWith.email))
            } else {
                share.sharedWith
            }

        val maskedCreatedBy =
            if (share.createdBy.id != currentUser.id) {
                share.createdBy.copy(email = maskEmail(share.createdBy.email))
            } else {
                share.createdBy
            }

        return share.copy(
            portfolio = share.portfolio?.let { it.copy(owner = maskedOwner ?: it.owner) },
            sharedWith = maskedSharedWith,
            createdBy = maskedCreatedBy
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioShareService::class.java)

        /**
         * Mask an email address: m***h@gmail.com
         */
        fun maskEmail(email: String): String {
            val atIndex = email.indexOf('@')
            if (atIndex <= 1) return email
            val local = email.substring(0, atIndex)
            val domain = email.substring(atIndex)
            if (local.length <= 2) {
                return "${local.first()}*$domain"
            }
            return "${local.first()}${"*".repeat(local.length - 2)}${local.last()}$domain"
        }
    }
}