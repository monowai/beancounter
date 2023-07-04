package com.beancounter.marketdata.registration

import com.beancounter.auth.AuthUtilService
import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest

private const val email = "data.email"

private const val auth0 = "data.auth0"

private const val active = "data.active"

private const val google = "data.googleId"

private const val auth0Id = "auth0Id"

private const val googleId = "googleId"

private const val userEmail = "user@email.com"

private const val gmail = "email@gmail.com"

/**
 * Verify service registration capabilities for various authentication providers.
 */
@SpringBootTest
@EntityScan("com.beancounter.common.model")
@AutoConfigureMockAuth
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
        val auth0User = SystemUser(email = "", auth0 = auth0Id)
        authUtilService.authenticate(auth0User, AuthUtilService.AuthProvider.AUTH0)
        assertThrows(BusinessException::class.java) { systemUserService.register() }
    }

    @Test
    fun registerAuth0User() {
        val auth0User = SystemUser(email = "auth0", auth0 = auth0Id)
        authUtilService.authenticate(auth0User, AuthUtilService.AuthProvider.AUTH0)
        val result = systemUserService.register()
        assertThat(result).isNotNull
            .hasFieldOrPropertyWithValue(email, auth0User.email)
            .hasFieldOrPropertyWithValue(auth0, auth0User.auth0)
            .hasFieldOrPropertyWithValue(active, true)
            .hasFieldOrPropertyWithValue(google, "")

        assertThat(systemUserService.getActiveUser()).isNotNull
    }

    @Test
    fun registerGoogleUser() {
        val googleUser = SystemUser(email = "gmail", googleId = googleId)
        authUtilService.authenticate(googleUser, AuthUtilService.AuthProvider.GOOGLE)
        val result = systemUserService.register()
        assertThat(result).isNotNull
            .hasFieldOrPropertyWithValue(email, googleUser.email)
            .hasFieldOrPropertyWithValue(active, true)
            .hasFieldOrPropertyWithValue(google, googleUser.googleId)

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
            SystemUser(email = userEmail, auth0 = auth0Id),
            AuthUtilService.AuthProvider.AUTH0,
        )
        systemUserService.register()
        authUtilService.authenticate(
            SystemUser(email = userEmail, googleId = googleId),
            AuthUtilService.AuthProvider.GOOGLE,
        )
        assertThat(systemUserService.register()).isNotNull
            .hasFieldOrPropertyWithValue(email, userEmail)
            .hasFieldOrPropertyWithValue(active, true)
            .hasFieldOrPropertyWithValue(google, googleId)
            .hasFieldOrPropertyWithValue(auth0, auth0Id)
    }

    @Test
    fun googleIdAndAuth0() {
        // Inverse test
        authUtilService.authenticate(
            SystemUser(email = gmail, googleId = googleId),
            AuthUtilService.AuthProvider.GOOGLE,
        )
        systemUserService.register()
        authUtilService.authenticate(
            SystemUser(email = gmail, auth0 = auth0Id),
            AuthUtilService.AuthProvider.AUTH0,
        )
        assertThat(systemUserService.register()).isNotNull
            .hasFieldOrPropertyWithValue(email, gmail)
            .hasFieldOrPropertyWithValue(active, true)
            .hasFieldOrPropertyWithValue(google, googleId)
            .hasFieldOrPropertyWithValue(auth0, auth0Id)
    }

    @Test
    fun systemAccount() {
        val systemUser = SystemUser(email = "", auth0 = googleId)
        authUtilService.authenticateM2M(
            systemUser,
            AuthUtilService.AuthProvider.AUTH0,
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
