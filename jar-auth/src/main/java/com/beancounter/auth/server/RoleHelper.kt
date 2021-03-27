package com.beancounter.auth.server

import com.beancounter.common.model.SystemUser
import org.springframework.security.core.authority.SimpleGrantedAuthority

object RoleHelper {
    const val ROLE_USER = "ROLE_user"
    const val ROLE_M2M = "ROLE_m2m"

    @JvmField
    val AUTH_M2M = SimpleGrantedAuthority(ROLE_M2M)
    const val SCOPE_BC = "SCOPE_beancounter"
    const val OAUTH_USER = "user"
    const val OAUTH_M2M = "m2m"
    val m2mSystemUser = SystemUser(id = OAUTH_M2M)
    const val OAUTH_ADMIN = "admin"
    const val SCOPE = "beancounter profile email"
}
