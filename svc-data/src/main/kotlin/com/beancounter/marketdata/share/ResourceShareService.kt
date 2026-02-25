package com.beancounter.marketdata.share

import com.beancounter.common.contracts.PendingResourceSharesResponse
import com.beancounter.common.contracts.ResourceShareInviteRequest
import com.beancounter.common.contracts.ResourceShareRequestAccess
import com.beancounter.common.contracts.ShareAccessCheck
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.ResourceShare
import com.beancounter.common.model.ShareAccessLevel
import com.beancounter.common.model.ShareResourceType
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.portfolio.PortfolioShareService
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Instant

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
    /**
     * Owner invites an adviser to view specific resources.
     */
    fun inviteAdviser(request: ResourceShareInviteRequest): Collection<ResourceShare> {
        val owner = systemUserService.getOrThrow()
        val adviser = resolveUserByEmail(request.adviserEmail)
        if (adviser.id == owner.id) {
            throw BusinessException("Cannot share resources with yourself")
        }

        return request.resourceIds.map { resourceId ->
            val existing =
                resourceShareRepository
                    .findByResourceTypeAndResourceIdAndSharedWith(request.resourceType, resourceId, adviser)
            if (existing.isPresent) {
                val share = existing.get()
                if (share.status == ShareStatus.ACTIVE || share.status == ShareStatus.PENDING_CLIENT_INVITE) {
                    throw BusinessException("Resource is already shared with this user")
                }
            }

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
        val client = resolveUserByEmail(request.clientEmail)
        if (client.id == adviser.id) {
            throw BusinessException("Cannot request access to your own resources")
        }

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
    fun acceptShare(shareId: String): ResourceShare {
        val currentUser = systemUserService.getOrThrow()
        val share = findShareOrThrow(shareId)
        validateShareAcceptance(share, currentUser, shareId)
        share.status = ShareStatus.ACTIVE
        share.acceptedAt = Instant.now()
        return resourceShareRepository.save(share)
    }

    private fun validateShareAcceptance(
        share: ResourceShare,
        currentUser: SystemUser,
        shareId: String
    ) {
        val authorised =
            when (share.status) {
                ShareStatus.PENDING_CLIENT_INVITE -> share.sharedWith.id == currentUser.id
                ShareStatus.PENDING_ADVISER_REQUEST ->
                    (share.targetUser ?: throw BusinessException("Share $shareId has no target user"))
                        .id == currentUser.id
                else -> throw BusinessException("Share is not pending: $shareId")
            }
        if (!authorised) {
            throw NotFoundException("Share not found: $shareId")
        }
    }

    /**
     * Either party can revoke a share.
     */
    fun revokeShare(shareId: String): ResourceShare {
        val currentUser = systemUserService.getOrThrow()
        val share = findShareOrThrow(shareId)

        val isOwner = share.resourceOwner.id == currentUser.id
        val isAdviser = share.sharedWith.id == currentUser.id
        val isTarget = share.targetUser?.id == currentUser.id
        if (!isOwner && !isAdviser && !isTarget) {
            throw NotFoundException("Share not found: $shareId")
        }

        share.status = ShareStatus.REVOKED
        return resourceShareRepository.save(share)
    }

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

    private fun findShareOrThrow(shareId: String): ResourceShare =
        resourceShareRepository.findById(shareId).orElseThrow {
            NotFoundException("Share not found: $shareId")
        }

    private fun resolveUserByEmail(email: String): SystemUser =
        systemUserService.findByEmail(email)
            ?: throw NotFoundException("User not found: ${PortfolioShareService.maskEmail(email)}")

    private fun maskShareEmails(
        share: ResourceShare,
        currentUser: SystemUser
    ): ResourceShare {
        val maskedSharedWith =
            if (share.sharedWith.id != currentUser.id) {
                share.sharedWith.copy(email = PortfolioShareService.maskEmail(share.sharedWith.email))
            } else {
                share.sharedWith
            }

        val maskedCreatedBy =
            if (share.createdBy.id != currentUser.id) {
                share.createdBy.copy(email = PortfolioShareService.maskEmail(share.createdBy.email))
            } else {
                share.createdBy
            }

        val maskedOwner =
            if (share.resourceOwner.id != currentUser.id) {
                share.resourceOwner.copy(email = PortfolioShareService.maskEmail(share.resourceOwner.email))
            } else {
                share.resourceOwner
            }

        return share.copy(
            sharedWith = maskedSharedWith,
            createdBy = maskedCreatedBy,
            resourceOwner = maskedOwner
        )
    }
}