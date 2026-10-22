package com.beancounter.agent.quota

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.exception.BusinessException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The weekly AI quota: the caller reads their own; an administrator reads,
 * sets and clears a user's limit, addressed by `SystemUser.id`.
 */
@RestController
@RequestMapping("/agent/quota")
@Tag(name = "Agent quota", description = "Weekly AI turn quota for callers without beancounter:ai")
class AgentQuotaController(
    private val quota: AgentQuota,
    private val owner: ConversationOwner,
    private val scopes: AgentScopeAuthorizer
) {
    @GetMapping
    @Operation(summary = "The caller's weekly AI quota; unlimited with beancounter:ai; verified = email confirmed")
    fun mine(): QuotaResponse =
        QuotaResponse(
            if (scopes.unlimited()) {
                quota.unlimited()
            } else {
                quota.statusOf(owner.id())
            }.copy(verified = scopes.emailConfirmed())
        )

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(summary = "The default limit and every user with use this week or an override (admin)")
    fun overview(): QuotaOverviewResponse = QuotaOverviewResponse(quota.overview())

    @GetMapping("/{systemUserId}")
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(summary = "A user's weekly AI quota (admin)")
    fun of(
        @PathVariable systemUserId: String
    ): QuotaResponse = QuotaResponse(quota.statusOf(systemUserId))

    @PutMapping("/{systemUserId}")
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(
        summary = "Set a user's weekly AI limit (0 disables AI) or grant unlimited, replacing the default (admin)"
    )
    fun set(
        @PathVariable systemUserId: String,
        @RequestBody request: QuotaLimitRequest
    ): QuotaResponse {
        val limit = request.weeklyLimit
        val status =
            when {
                request.unlimited && limit == null -> quota.setUnlimited(systemUserId)
                !request.unlimited && limit != null -> quota.setLimit(systemUserId, limit)
                else -> throw BusinessException("Send either weeklyLimit or unlimited=true")
            }
        return QuotaResponse(status)
    }

    @DeleteMapping("/{systemUserId}")
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(summary = "Return a user to the default weekly AI limit (admin)")
    fun clear(
        @PathVariable systemUserId: String
    ): QuotaResponse = QuotaResponse(quota.clearLimit(systemUserId))
}

/** Either a [weeklyLimit] (0 disables AI) or [unlimited], never both. */
data class QuotaLimitRequest(
    val weeklyLimit: Int? = null,
    val unlimited: Boolean = false
)

data class QuotaOverviewResponse(
    val data: QuotaOverview
)

data class QuotaResponse(
    val data: QuotaStatus
)