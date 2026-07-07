package com.beancounter.marketdata.broker

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.RegistrationUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val BROKERS_ROOT = "/brokers"

/**
 * Verifies BrokerController is guarded by the same scope-based authorization as its peers.
 *
 * The rejection case registers the caller first (so business logic such as
 * `SystemUserService.getOrThrow` would succeed if reached) then grants only
 * `beancounter:admin` — an authority that satisfies the global api-path
 * filter gate (ADMIN is one of the accepted authorities there) but does not
 * satisfy BrokerController's own class-level `@PreAuthorize` (USER or
 * SYSTEM only). This isolates the controller's own guard from both the
 * separate deny-by-default filter change and the unregistered-user business
 * exception, so this test only goes green because of BrokerController's own
 * `@PreAuthorize`.
 */
@SpringMvcDbTest
internal class BrokerControllerAuthTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `token without user or system authority is rejected even when registered`() {
        val token =
            RegistrationUtils.registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(SystemUser())
            )

        mockMvc
            .perform(
                get(BROKERS_ROOT)
                    .with(
                        jwt()
                            .jwt(token)
                            .authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_ADMIN))
                    )
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `token with user scope is accepted`() {
        val token =
            RegistrationUtils.registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(SystemUser())
            )

        mockMvc
            .perform(
                get(BROKERS_ROOT)
                    .with(jwt().jwt(token))
            ).andExpect(status().isOk)
    }
}