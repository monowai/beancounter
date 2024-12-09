package com.beancounter.marketdata.registration

import com.beancounter.auth.AuthUtilService
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

private const val EMAIL = "data.email"

private const val AUTH0 = "data.auth0"

private const val ACTIVE = "data.active"

private const val GOOGLE = "data.googleId"

private const val AUTH0ID = "auth0Id"

private const val GOOGLEID = "googleId"

private const val USER_EMAIL = "user@email.com"

private const val GMAIL = "email@gmail.com"

/**
 * Verify service registration capabilities for various authentication providers.
 */
@SpringMvcDbTest
class SystemUserServiceTest {
    @Autowired
    lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var systemUserService: SystemUserService

    @Autowired
    lateinit var systemUserRepository: SystemUserRepository

    @Autowired
    lateinit var authUtilService: AuthUtilService

    @Test
    fun registerUserWithNoEmailFails() {
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
    fun registerAuth0User() {
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
    fun registerGoogleUser() {
        val googleUser =
            SystemUser(
                email = "gmail",
                googleId = GOOGLEID
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
        assertThat(systemUserService.getOrThrow).isNotNull
    }

    @Test
    fun unauthenticatedUserCanNotRegister() {
        mockAuthConfig.resetAuth()
        assertThrows(UnauthorizedException::class.java) {
            systemUserService.register()
        }
    }

    @Test
    fun auth0AndGoogleId() {
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
                googleId = GOOGLEID
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
                GOOGLEID
            ).hasFieldOrPropertyWithValue(
                AUTH0,
                AUTH0ID
            )
    }

    @Test
    fun googleIdAndAuth0() {
        // Inverse test
        authUtilService.authenticate(
            SystemUser(
                email = GMAIL,
                googleId = GOOGLEID
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
                GOOGLEID
            ).hasFieldOrPropertyWithValue(
                AUTH0,
                AUTH0ID
            )
    }

    @Test
    fun systemAccount() {
        val systemUser =
            SystemUser(
                email = "",
                auth0 = GOOGLEID
            )
        authUtilService.authenticateM2M(
            systemUser,
            AuthUtilService.AuthProvider.AUTH0
        )
        assertThrows(BusinessException::class.java) {
            systemUserService.register()
        }
        assertThat(systemUserService.isServiceAccount()).isTrue()
        assertThat(systemUserService.getOrThrow).isNotNull

        // Make sure we can't register a system account.
        assertThrows(BusinessException::class.java) {
            systemUserService.register()
        }
    }
}