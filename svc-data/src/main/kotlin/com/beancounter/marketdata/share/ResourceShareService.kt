package com.beancounter.marketdata.share

import com.beancounter.common.contracts.PendingResourceSharesResponse
import com.beancounter.common.contracts.ResourceShareInviteRequest
import com.beancounter.common.contracts.ResourceShareRequestAccess
import com.beancounter.common.contracts.ShareAccessCheck
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.ResourceShare
import com.beancounter.common.model.ShareAccessLevel
import com.beancounter.common.model.ShareResourceType
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

/**
 * Business logic for sharing non-portfolio resources (plans, models)
 * between clients and advisers.
 */
@Service
@Transactional
class ResourceShareService(
    private val resourceShareRepository: ResourceShareRepository,
    private val systemUserService: SystemUserService
) {
    private val shares = ShareLifecycle(resourceShareRepository, systemUserService)

    /**
     * Owner invites an adviser to view specific resources.
     */
    fun inviteAdviser(request: ResourceShareInviteRequest): Collection<ResourceShare> {
        val owner = systemUserService.getOrThrow()
        val adviser = shares.counterparty(request.adviserEmail, owner, "Cannot share resources with yourself")

        return request.resourceIds.map { resourceId ->
            shares.requireNotShared(
                resourceShareRepository
                    .findByResourceTypeAndResourceIdAndSharedWith(request.resourceType, resourceId, adviser),
                "Resource is already shared with this user"
            )

            resourceShareRepository.save(
                ResourceShare(
                    resourceType = request.resourceType,
                    resourceId = resourceId,
                    sharedWith = adviser,
                    accessLevel = request.accessLevel,
                    status = ShareStatus.PENDING_CLIENT_INVITE,
                    createdBy = owner,
                    resourceOwner = owner
                )
            )
        }
    }

    /**
     * Adviser requests access from a client for a given resource type.
     */
    fun requestAccess(request: ResourceShareRequestAccess): ResourceShare {
        val adviser = systemUserService.getOrThrow()
        val client = shares.counterparty(request.clientEmail, adviser, "Cannot request access to your own resources")

        val existingRequests =
            resourceShareRepository
                .findByTargetUserAndStatus(client, ShareStatus.PENDING_ADVISER_REQUEST)
        if (existingRequests.any {
                it.sharedWith.id == adviser.id && it.resourceType == request.resourceType
            }
        ) {
            throw BusinessException("You already have a pending request with this client")
        }

        return resourceShareRepository.save(
            ResourceShare(
                resourceType = request.resourceType,
                resourceId = "",
                sharedWith = adviser,
                accessLevel = ShareAccessLevel.VIEW,
                status = ShareStatus.PENDING_ADVISER_REQUEST,
                createdBy = adviser,
                resourceOwner = client,
                targetUser = client
            )
        )
    }

    /**
     * Accept a pending invitation or request.
     */
    fun acceptShare(shareId: String): ResourceShare = shares.accept(shareId)

    /**
     * Either party can revoke a share.
     */
    fun revokeShare(shareId: String): ResourceShare = shares.revoke(shareId)

    /**
     * Pending notifications for the current user across all resource types.
     */
    fun getPendingNotifications(): PendingResourceSharesResponse {
        val currentUser = systemUserService.getOrThrow()

        val invites =
            resourceShareRepository
                .findBySharedWithAndStatusIn(
                    currentUser,
                    listOf(ShareStatus.PENDING_CLIENT_INVITE)
                ).toList()
                .map { maskShareEmails(it, currentUser) }

        val incomingRequests =
            resourceShareRepository
                .findByTargetUserAndStatus(
                    currentUser,
                    ShareStatus.PENDING_ADVISER_REQUEST
                ).toList()
                .map { maskShareEmails(it, currentUser) }

        return PendingResourceSharesResponse(
            invites = invites,
            requests = incomingRequests
        )
    }

    /**
     * Get active shared resources of a given type for the current adviser.
     */
    fun getManagedResources(resourceType: ShareResourceType): Collection<ResourceShare> {
        val adviser = systemUserService.getOrThrow()
        return resourceShareRepository
            .findBySharedWithAndResourceTypeAndStatus(
                adviser,
                resourceType,
                ShareStatus.ACTIVE
            ).toList()
            .map { maskShareEmails(it, adviser) }
    }

    /**
     * Outbound shares the caller has granted for resources of `resourceType`.
     * Excludes REVOKED. Mirrors PortfolioShareService.getGrantedShares —
     * used by the central "Shares I've granted" surface.
     */
    fun getGrantedShares(resourceType: ShareResourceType): Collection<ResourceShare> {
        val owner = systemUserService.getOrThrow()
        return resourceShareRepository
            .findByResourceOwnerAndResourceTypeAndStatusNot(
                owner,
                resourceType,
                ShareStatus.REVOKED
            ).toList()
    }

    /**
     * Check if the current user has active access to a specific resource.
     */
    fun checkAccess(
        resourceType: ShareResourceType,
        resourceId: String
    ): ShareAccessCheck {
        val currentUser = systemUserService.getOrThrow()
        val share =
            resourceShareRepository
                .findByResourceTypeAndResourceIdAndSharedWith(resourceType, resourceId, currentUser)
        val hasAccess = share.isPresent && share.get().status == ShareStatus.ACTIVE
        return ShareAccessCheck(
            resourceType = resourceType,
            resourceId = resourceId,
            userId = currentUser.id,
            hasAccess = hasAccess,
            accessLevel = if (hasAccess) share.get().accessLevel else null
        )
    }

    private fun maskShareEmails(
        share: ResourceShare,
        currentUser: SystemUser
    ): ResourceShare =
        share.copy(
            sharedWith = maskUnlessCaller(share.sharedWith, currentUser),
            createdBy = maskUnlessCaller(share.createdBy, currentUser),
            resourceOwner = maskUnlessCaller(share.resourceOwner, currentUser)
        )
}