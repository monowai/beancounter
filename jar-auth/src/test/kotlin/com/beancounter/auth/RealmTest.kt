package com.beancounter.auth

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.JwtRoleConverter
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.KeyGenUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

/**
 * Converts OAuth realms to Spring Authorities
 */
class RealmTest {
    @Test
    fun roleConvertedFromOAuthToGrantedAuth() {
        val jwtRoleConverter = JwtRoleConverter("empty", "nothing")
        val su = SystemUser(KeyGenUtils().format(UUID.randomUUID()))
        assertThat(
            jwtRoleConverter.getAuthorities(
                TokenUtils().getUserToken(su)
            )
        )
            .hasSize(3)
            .containsExactlyInAnyOrder( // Default scopes
                SimpleGrantedAuthority("SCOPE_email"),
                SimpleGrantedAuthority("SCOPE_profile"),
                SimpleGrantedAuthority("SCOPE_beancounter")
            )
    }
}
