package com.beancounter.auth.common

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.model.SystemUser
import org.springframework.security.oauth2.jwt.Jwt
import java.util.ArrayList
import java.util.Collections
import java.util.Date
import java.util.HashMap

/**
 * TestHelper class to generate JWT tokens you can test with.
 */
class TokenUtils {
    fun getUserToken(systemUser: SystemUser): Jwt {
        return getUserToken(systemUser, defaultRoles)
    }

    fun getUserToken(systemUser: SystemUser, realmAccess: Map<String?, Collection<String?>?>?): Jwt {
        return Jwt.withTokenValue(systemUser.id)
            .header("alg", "none")
            .subject(systemUser.id)
            .claim("email", systemUser.email)
            .claim("realm_access", realmAccess)
            .claim("scope", AuthConstants.SCOPE)
            .expiresAt(Date(System.currentTimeMillis() + 60000).toInstant())
            .build()
    }

    private val defaultRoles: Map<String?, Collection<String?>?>
        get() = getRoles("user")

    fun getRoles(vararg roles: String?): Map<String?, Collection<String?>?> {
        val realmAccess: MutableMap<String?, Collection<String?>?> = HashMap()
        val userRoles: MutableCollection<String?> = ArrayList()
        Collections.addAll(userRoles, *roles)
        realmAccess["roles"] = userRoles
        return realmAccess
    }
}
