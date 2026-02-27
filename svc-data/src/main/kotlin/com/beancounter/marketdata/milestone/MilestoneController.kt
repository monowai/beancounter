package com.beancounter.marketdata.milestone

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.model.UserMilestone
import com.beancounter.marketdata.registration.SystemUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for user milestone tracking.
 */
@RestController
@RequestMapping("/api/milestones")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
)
@Tag(name = "Milestones", description = "Track user milestones and feature discovery")
class MilestoneController(
    private val milestoneService: MilestoneService,
    private val systemUserService: SystemUserService
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "Get all milestones, explorer actions, and notification mode")
    fun getMilestones(): MilestonesResponse {
        val user = systemUserService.getOrThrow()
        return MilestonesResponse(
            earned = milestoneService.getEarnedMilestones(user),
            explorerActions = milestoneService.getExplorerActions(user).map { it.actionId },
            mode = milestoneService.getMilestoneMode(user)
        )
    }

    @PostMapping(
        value = ["/earn"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Record a newly earned milestone (tier only upgrades, never downgrades)")
    fun earnMilestone(
        @RequestBody request: EarnMilestoneRequest
    ): UserMilestone {
        val user = systemUserService.getOrThrow()
        return milestoneService.earnMilestone(user, request.milestoneId, request.tier)
    }

    @PostMapping(
        value = ["/explore"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Record a feature discovery action (idempotent)")
    fun recordExplorerAction(
        @RequestBody request: ExplorerActionRequest
    ) {
        val user = systemUserService.getOrThrow()
        milestoneService.recordExplorerAction(user, request.actionId)
    }
}