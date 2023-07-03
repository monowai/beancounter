package com.beancounter.marketdata.registration

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.UserUtils
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
    lateinit var userUtils: UserUtils

    @Test
    fun registerAuth0User() {
        val auth0User = SystemUser(email = "auth0", auth0 = "auth0Id")
        userUtils.authenticate(auth0User, UserUtils.AuthProvider.AUTH0)
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
        val googleUser = SystemUser(email = "gmail", googleId = "googleId")
        userUtils.authenticate(googleUser, UserUtils.AuthProvider.GOOGLE)
        val result = systemUserService.register()
        assertThat(result).isNotNull
            .hasFieldOrPropertyWithValue(email, googleUser.email)
            .hasFieldOrPropertyWithValue(active, true)
            .hasFieldOrPropertyWithValue(google, googleUser.googleId)

        assertThat(systemUserService.getActiveUser()).isNotNull
    }

    @Test
    fun unauthenticatedUserCanNotRegister() {
        mockAuthConfig.resetAuth()
        assertThrows(UnauthorizedException::class.java) {
            systemUserService.register()
        }
    }
}
