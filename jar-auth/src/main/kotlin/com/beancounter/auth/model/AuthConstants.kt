package com.beancounter.auth.model

import com.beancounter.common.model.SystemUser
import org.springframework.security.oauth2.jwt.Jwt

/**
 * OAUTH related constants
 */
object AuthConstants {
    const val APP_NAME = "beancounter"
    const val USER = "$APP_NAME:user"
    const val SYSTEM = "$APP_NAME:system"
    const val ADMIN = "$APP_NAME:admin"

    const val SCOPE_BC = "SCOPE_$APP_NAME"
    const val SCOPE_USER = "SCOPE_$USER"
    const val SCOPE_SYSTEM = "SCOPE_$SYSTEM"
    const val SCOPE_ADMIN = "SCOPE_$ADMIN"

    val authSystem = SystemUser(id = SYSTEM)

    /**
     * Check if the JWT represents an admin user.
     */
    fun isAdmin(jwt: Jwt): Boolean {
        val scope = jwt.getClaimAsString("scope") ?: ""
        return scope.contains(ADMIN)
    }
}