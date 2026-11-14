package com.beancounter.marketdata.registration

import com.beancounter.auth.AuthUtilService
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

private const val RELAY_EMAIL = "x7k2m9@privaterelay.appleid.com"

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
    lateinit var systemUserRegistration: SystemUserRegistration

    @Autowired
    lateinit var authUtilService: AuthUtilService

    @Autowired
    lateinit var systemUserRepository: SystemUserRepository

    @Autowired
    lateinit var systemUserCache: SystemUserCache

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
        assertThrows(BusinessException::class.java) { systemUserRegistration.register() }
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
        val result = systemUserRegistration.register()
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
        val result = systemUserRegistration.register()
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
            systemUserRegistration.register()
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
        systemUserRegistration.register()
        authUtilService.authenticate(
            SystemUser(
                email = USER_EMAIL,
                googleId = GOOGLE_ID
            ),
            AuthUtilService.AuthProvider.GOOGLE
        )
        assertThat(systemUserRegistration.register())
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
        systemUserRegistration.register()
        authUtilService.authenticate(
            SystemUser(
                email = GMAIL,
                auth0 = AUTH0ID
            ),
            AuthUtilService.AuthProvider.AUTH0
        )
        assertThat(systemUserRegistration.register())
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
    fun `should reactivate a deactivated user on re-registration`() {
        val auth0User =
            SystemUser(
                email = "reactivate@auth0",
                auth0 = AUTH0ID
            )
        authUtilService.authenticate(
            auth0User,
            AuthUtilService.AuthProvider.AUTH0
        )
        val first = systemUserRegistration.register().data
        assertThat(first.active).isTrue()

        // Simulate the offboarding soft-delete: mark inactive in the DB and drop
        // the cache (deleteUserAccount's deactivate path is covered by
        // OffboardingServiceTest).
        systemUserRepository.save(
            systemUserRepository.findById(first.id).get().apply { active = false }
        )
        systemUserCache.evictAll()
        assertThat(systemUserRepository.findById(first.id).get().active).isFalse()

        // Deactivated user is invisible to the default resolver (forces logout),
        // but still findable for the /register reactivation path.
        assertThat(systemUserService.getActiveUser()).isNull()
        assertThat(systemUserService.getActiveUser(includeInactive = true)).isNotNull

        // Signing up again reactivates the SAME account, not a new row.
        val second = systemUserRegistration.register().data
        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.active).isTrue()
        assertThat(systemUserRepository.findById(first.id).get().active).isTrue()
    }

    @Test
    fun `should handle system account correctly`() {
        val systemUser =
            SystemUser(
                email = "",
                auth0 = GOOGLE_ID
            )
        authUtilService.authenticateM2M(
            systemUser
        )
        assertThrows(BusinessException::class.java) {
            systemUserRegistration.register()
        }
        assertThat(systemUserService.isServiceAccount()).isTrue()
        assertThat(systemUserService.getOrThrow()).isNotNull

        // Make sure we can't register a system account.
        assertThrows(BusinessException::class.java) {
            systemUserRegistration.register()
        }
    }

    @Test
    fun `should register an Apple user under the Apple subject`() {
        val appleUser = SystemUser(email = "owner@icloud.test", appleId = "apple|registered")
        authUtilService.authenticate(appleUser, AuthUtilService.AuthProvider.APPLE)

        val registered = systemUserRegistration.register().data

        assertThat(registered.appleId).isEqualTo("apple|registered")
        assertThat(registered.email).isEqualTo(appleUser.email)
        assertThat(registered.auth0).isEmpty()
        assertThat(registered.googleId).isEmpty()
        assertThat(systemUserService.getActiveUser()?.id).isEqualTo(registered.id)
    }

    @Test
    fun `should find an Apple user by subject when the caller has no email`() {
        authUtilService.authenticate(
            SystemUser(email = "by-subject@icloud.test", appleId = "apple|by-subject"),
            AuthUtilService.AuthProvider.APPLE
        )
        val registered = systemUserRegistration.register().data

        assertThat(systemUserService.findByExternal("apple|by-subject", null)?.id).isEqualTo(registered.id)
    }

    @Test
    fun `should refuse an Apple relay address as the owning email`() {
        authUtilService.authenticate(
            SystemUser(email = RELAY_EMAIL, appleId = "apple|relay"),
            AuthUtilService.AuthProvider.APPLE
        )

        assertThatThrownBy { systemUserRegistration.register() }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage(SystemUserRegistration.RELAY_EMAIL_REFUSED)
        assertThat(systemUserRepository.findByEmail(RELAY_EMAIL)).isEmpty()
        assertThat(systemUserRepository.findByAppleId("apple|relay")).isEmpty()
    }

    @Test
    fun `should refuse a relay address whatever its case`() {
        authUtilService.authenticate(
            SystemUser(email = "X7K2M9@PrivateRelay.AppleID.com", appleId = "apple|relay-case"),
            AuthUtilService.AuthProvider.APPLE
        )

        assertThatThrownBy { systemUserRegistration.register() }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `should know a registered Apple user who later arrives with a relay address`() {
        authUtilService.authenticate(
            SystemUser(email = "known@icloud.test", appleId = "apple|returning"),
            AuthUtilService.AuthProvider.APPLE
        )
        val first = systemUserRegistration.register().data

        authUtilService.authenticate(
            SystemUser(email = RELAY_EMAIL, appleId = "apple|returning"),
            AuthUtilService.AuthProvider.APPLE
        )
        val second = systemUserRegistration.register().data

        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.email).isEqualTo("known@icloud.test")
    }

    @Test
    fun `should record the Apple subject on the account its email already owns`() {
        authUtilService.authenticate(
            SystemUser(email = "both@gmail.test", googleId = GOOGLE_ID),
            AuthUtilService.AuthProvider.GOOGLE
        )
        val google = systemUserRegistration.register().data
        systemUserRepository.save(
            systemUserRepository.findById(google.id).get().apply { cashPortfolioId = "cash-1" }
        )
        systemUserCache.evictAll()

        authUtilService.authenticate(
            SystemUser(email = "both@gmail.test", appleId = "apple|linked"),
            AuthUtilService.AuthProvider.APPLE
        )
        systemUserRegistration.register()

        val stored = systemUserRepository.findById(google.id).get()
        assertThat(stored.appleId).isEqualTo("apple|linked")
        assertThat(stored.googleId).isEqualTo(GOOGLE_ID)
        assertThat(stored.cashPortfolioId).isEqualTo("cash-1")
    }
}