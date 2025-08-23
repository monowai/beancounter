package com.beancounter.marketdata.registration

import com.beancounter.auth.AuthUtilService
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

private const val EMAIL = "data.email"

private const val AUTH0 = "data.auth0"

private const val ACTIVE = "data.active"

private const val GOOGLE = "data.googleId"

private const val AUTH0ID = "auth0Id"

private const val GOOGLE_ID = "googleId"

private const val USER_EMAIL = "user@email.com"

private const val GMAIL = "email@gmail.com"

/**
 * Verify service registration capabilities for various authentication providers.
 */
@SpringMvcDbTest
class SystemUserServiceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    lateinit var systemUserService: SystemUserService

    @Autowired
    lateinit var authUtilService: AuthUtilService

    @Test
    fun `should fail to register user with no email`() {
        val auth0User =
            SystemUser(
                email = "",
                auth0 = AUTH0ID
            )
        authUtilService.authenticate(
            auth0User,
            AuthUtilService.AuthProvider.AUTH0
        )
        assertThrows(BusinessException::class.java) { systemUserService.register() }
    }

    @Test
    fun `should register Auth0 user successfully`() {
        val auth0User =
            SystemUser(
                email = "auth0",
                auth0 = AUTH0ID
            )
        authUtilService.authenticate(
            auth0User,
            AuthUtilService.AuthProvider.AUTH0
        )
        val result = systemUserService.register()
        assertThat(result)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                EMAIL,
                auth0User.email
            ).hasFieldOrPropertyWithValue(
                AUTH0,
                auth0User.auth0
            ).hasFieldOrPropertyWithValue(
                ACTIVE,
                true
            ).hasFieldOrPropertyWithValue(
                GOOGLE,
                ""
            )

        assertThat(systemUserService.getActiveUser()).isNotNull
    }

    @Test
    fun `should register Google user successfully`() {
        val googleUser =
            SystemUser(
                email = "gmail",
                googleId = GOOGLE_ID
            )
        authUtilService.authenticate(
            googleUser,
            AuthUtilService.AuthProvider.GOOGLE
        )
        val result = systemUserService.register()
        assertThat(result)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                EMAIL,
                googleUser.email
            ).hasFieldOrPropertyWithValue(
                ACTIVE,
                true
            ).hasFieldOrPropertyWithValue(
                GOOGLE,
                googleUser.googleId
            )

        assertThat(systemUserService.getActiveUser()).isNotNull
        assertThat(systemUserService.getOrThrow()).isNotNull
    }

    @Test
    fun `should not allow unauthenticated user to register`() {
        mockAuthConfig.logout()
        assertThrows(UnauthorizedException::class.java) {
            systemUserService.register()
        }
    }

    @Test
    fun `should handle Auth0 and Google ID for same user`() {
        authUtilService.authenticate(
            SystemUser(
                email = USER_EMAIL,
                auth0 = AUTH0ID
            ),
            AuthUtilService.AuthProvider.AUTH0
        )
        systemUserService.register()
        authUtilService.authenticate(
            SystemUser(
                email = USER_EMAIL,
                googleId = GOOGLE_ID
            ),
            AuthUtilService.AuthProvider.GOOGLE
        )
        assertThat(systemUserService.register())
            .isNotNull
            .hasFieldOrPropertyWithValue(
                EMAIL,
                USER_EMAIL
            ).hasFieldOrPropertyWithValue(
                ACTIVE,
                true
            ).hasFieldOrPropertyWithValue(
                GOOGLE,
                GOOGLE_ID
            ).hasFieldOrPropertyWithValue(
                AUTH0,
                AUTH0ID
            )
    }

    @Test
    fun `should handle Google ID and Auth0 for same user`() {
        // Inverse test
        authUtilService.authenticate(
            SystemUser(
                email = GMAIL,
                googleId = GOOGLE_ID
            ),
            AuthUtilService.AuthProvider.GOOGLE
        )
        systemUserService.register()
        authUtilService.authenticate(
            SystemUser(
                email = GMAIL,
                auth0 = AUTH0ID
            ),
            AuthUtilService.AuthProvider.AUTH0
        )
        assertThat(systemUserService.register())
            .isNotNull
            .hasFieldOrPropertyWithValue(
                EMAIL,
                GMAIL
            ).hasFieldOrPropertyWithValue(
                ACTIVE,
                true
            ).hasFieldOrPropertyWithValue(
                GOOGLE,
                GOOGLE_ID
            ).hasFieldOrPropertyWithValue(
                AUTH0,
                AUTH0ID
            )
    }

    @Test
    fun `should handle system account correctly`() {
        val systemUser =
            SystemUser(
                email = "",
                auth0 = GOOGLE_ID
            )
        authUtilService.authenticateM2M(
            systemUser,
            AuthUtilService.AuthProvider.AUTH0
        )
        assertThrows(BusinessException::class.java) {
            systemUserService.register()
        }
        assertThat(systemUserService.isServiceAccount()).isTrue()
        assertThat(systemUserService.getOrThrow()).isNotNull

        // Make sure we can't register a system account.
        assertThrows(BusinessException::class.java) {
            systemUserService.register()
        }
    }
}