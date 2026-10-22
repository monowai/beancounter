package com.beancounter.marketdata.registration

import com.beancounter.auth.model.AuthConstants
import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

/** Administrators list every SystemUser with when each was last seen. */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
class AdminUserController(
    private val systemUsers: SystemUserRepository,
    private val activity: UserActivityService
) {
    /** Most recently seen first; users not seen since tracking began last. */
    @GetMapping
    fun users(): AdminUsersResponse {
        val lastSeen = activity.lastSeen()
        val users =
            systemUsers.findAll().map {
                AdminUser(it.id, it.email, it.active, it.since, lastSeen[it.id])
            }
        // Descending with nulls first in the ascending order puts never-seen users last.
        return AdminUsersResponse(users.sortedWith(compareByDescending(nullsFirst()) { it.lastSeen }))
    }
}

data class AdminUser(
    val id: String,
    val email: String,
    val active: Boolean,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val since: LocalDate,
    val lastSeen: Instant?
)

data class AdminUsersResponse(
    val data: List<AdminUser>
)