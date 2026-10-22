package com.beancounter.marketdata.registration

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * An administrator lists every SystemUser with when each was last seen; a
 * user's own `/me` call is what marks them seen.
 */
@SpringMvcDbTest
class AdminUserControllerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var activity: UserActivityRepository

    private fun registered(id: String): Jwt =
        registerUser(
            mockMvc,
            mockAuthConfig.getUserToken(SystemUser(id = id, email = "$id@testing.com", auth0 = "auth0|$id"))
        )

    private val admin = jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_ADMIN))

    @Test
    fun `a user's call to me should mark them last seen in the admin list`() {
        val seen = registered("admin-list-seen")
        registered("admin-list-unseen")

        mockMvc.perform(get("/me").with(jwt().jwt(seen))).andExpect(status().isOk)

        mockMvc
            .perform(get("/admin/users").with(admin))
            .andExpect(status().isOk)
            .andExpect(
                jsonPath("$.data[?(@.email == 'admin-list-seen@testing.com')].lastSeen", contains(notNullValue()))
            ).andExpect(
                jsonPath("$.data[?(@.email == 'admin-list-unseen@testing.com')].lastSeen", contains(nullValue()))
            )
    }

    @Test
    fun `a non-administrator should not list users`() {
        val user = registered("admin-list-user")

        mockMvc
            .perform(
                get("/admin/users").with(jwt().jwt(user).authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_USER)))
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `last seen should be written at most once an hour`() {
        var now = Instant.parse("2026-10-22T00:00:00Z")
        val clock =
            object : Clock() {
                override fun getZone() = ZoneOffset.UTC

                override fun withZone(zone: java.time.ZoneId?) = this

                override fun instant() = now
            }
        val service = UserActivityService(activity, clock)
        val id = "activity-throttle"

        service.seen(id)
        now = now.plus(Duration.ofMinutes(59))
        service.seen(id)
        assertThat(activity.findById(id).get().lastSeen).isEqualTo(Instant.parse("2026-10-22T00:00:00Z"))

        now = now.plus(Duration.ofMinutes(2))
        service.seen(id)
        assertThat(activity.findById(id).get().lastSeen).isEqualTo(now)
    }
}