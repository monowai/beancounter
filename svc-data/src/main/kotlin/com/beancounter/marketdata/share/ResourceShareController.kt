package com.beancounter.marketdata.share

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PendingResourceSharesResponse
import com.beancounter.common.contracts.ResourceShareInviteRequest
import com.beancounter.common.contracts.ResourceShareRequestAccess
import com.beancounter.common.contracts.ResourceShareResponse
import com.beancounter.common.contracts.ResourceSharesResponse
import com.beancounter.common.contracts.ShareAccessCheck
import com.beancounter.common.model.ShareResourceType
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
@RequestMapping("/resource-shares")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Resource Sharing",
    description = "Share independence plans and rebalance models between clients and advisers"
)
class ResourceShareController internal constructor(
    private val resourceShareService: ResourceShareService
) {
    @PostMapping("/invite")
    @Operation(summary = "Invite an adviser to view selected resources")
    fun inviteAdviser(
        @RequestBody request: ResourceShareInviteRequest
    ): ResourceSharesResponse = ResourceSharesResponse(resourceShareService.inviteAdviser(request))

    @PostMapping("/request")
    @Operation(summary = "Request access to a client's resources")
    fun requestAccess(
        @RequestBody request: ResourceShareRequestAccess
    ): ResourceShareResponse = ResourceShareResponse(resourceShareService.requestAccess(request))

    @PostMapping("/{shareId}/accept")
    @Operation(summary = "Accept a pending resource share")
    fun acceptShare(
        @PathVariable shareId: String
    ): ResourceShareResponse = ResourceShareResponse(resourceShareService.acceptShare(shareId))

    @DeleteMapping("/{shareId}")
    @Operation(summary = "Revoke a resource share")
    fun revokeShare(
        @PathVariable shareId: String
    ): ResourceShareResponse = ResourceShareResponse(resourceShareService.revokeShare(shareId))

    @GetMapping("/pending")
    @Operation(summary = "Get pending resource share notifications")
    fun getPendingNotifications(): PendingResourceSharesResponse = resourceShareService.getPendingNotifications()

    @GetMapping("/managed/{resourceType}")
    @Operation(summary = "Get resources of a type shared with the current user")
    fun getManagedResources(
        @PathVariable resourceType: ShareResourceType
    ): ResourceSharesResponse = ResourceSharesResponse(resourceShareService.getManagedResources(resourceType))

    @GetMapping("/check/{resourceType}/{resourceId}")
    @Operation(summary = "Check if current user has access to a resource")
    fun checkAccess(
        @PathVariable resourceType: ShareResourceType,
        @PathVariable resourceId: String
    ): ShareAccessCheck = resourceShareService.checkAccess(resourceType, resourceId)
}