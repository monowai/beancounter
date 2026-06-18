package com.beancounter.common.contracts

import com.beancounter.common.model.ResourceShare
import com.beancounter.common.model.ShareAccessLevel
import com.beancounter.common.model.ShareResourceType

/**
 * Request/response contracts for resource sharing (plans, models).
 * Owner invites an adviser to view specific resources.
 */
data class ResourceShareInviteRequest(
    val resourceType: ShareResourceType,
    val resourceIds: List<String>,
    val adviserEmail: String,
    val accessLevel: ShareAccessLevel = ShareAccessLevel.VIEW
)

/**
 * Adviser requests access to a client's resources of a given type.
 */
data class ResourceShareRequestAccess(
    val resourceType: ShareResourceType,
    val clientEmail: String,
    val message: String? = null
)

data class ResourceShareResponse(
    override var data: ResourceShare
) : Payload<ResourceShare>

data class ResourceSharesResponse(
    override var data: Collection<ResourceShare>
) : Payload<Collection<ResourceShare>>

/**
 * Pending resource share notifications for the current user.
 */
data class PendingResourceSharesResponse(
    val invites: Collection<ResourceShare>,
    val requests: Collection<ResourceShare>
)

/**
 * Lightweight DTO for cross-service access checks.
 */
data class ShareAccessCheck(
    val resourceType: ShareResourceType,
    val resourceId: String,
    val userId: String,
    val hasAccess: Boolean,
    val accessLevel: ShareAccessLevel? = null
)