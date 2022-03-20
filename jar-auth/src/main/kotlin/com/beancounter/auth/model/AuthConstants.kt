package com.beancounter.auth.model

import com.beancounter.common.model.SystemUser
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * OAUTH related constants
 */
object AuthConstants {
    const val APP_NAME = "beancounter"
    const val USER = "$APP_NAME:user"
    const val SYSTEM = "$APP_NAME:system"
    const val ADMIN = "$APP_NAME:admin"

    const val ROLE_USER = "ROLE_$USER"
    const val ROLE_SYSTEM = "ROLE_$SYSTEM"
    const val ROLE_ADMIN = "ROLE_$ADMIN"

    @JvmField
    val AUTH_M2M = SimpleGrantedAuthority(ROLE_SYSTEM)
    const val SCOPE_BC = "SCOPE_$APP_NAME"
    const val SCOPE_USER = "SCOPE_$USER"
    const val SCOPE_SYSTEM = "SCOPE_$SYSTEM"
    const val SCOPE_ADMIN = "SCOPE_$ADMIN"
    val m2mSystemUser = SystemUser(id = SYSTEM)
    const val SCOPE = "$APP_NAME profile email $ADMIN $USER"
}
