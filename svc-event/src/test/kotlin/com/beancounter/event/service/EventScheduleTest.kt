package com.beancounter.event.service

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.utils.DateUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for EventSchedule to verify the scheduled event loading
 * authenticates via M2M and delegates to EventLoader.
 */
@ExtendWith(MockitoExtension::class)
class EventScheduleTest {
    @Mock
    private lateinit var eventService: EventService

    @Mock
    private lateinit var eventLoader: EventLoader

    @Mock
    private lateinit var loginService: LoginService

    @Mock
    private lateinit var authConfig: AuthConfig

    @Mock
    private lateinit var dateUtils: DateUtils

    private lateinit var eventSchedule: EventSchedule

    @BeforeEach
    fun setUp() {
        eventSchedule = EventSchedule(eventService, eventLoader, dateUtils)
        eventSchedule.setLoginService(loginService)
    }

    private fun setupAuth() {
        val token = OpenIdResponse(token = "test-token", scope = "openid", expiry = 3600, type = "Bearer")
        whenever(loginService.authConfig).thenReturn(authConfig)
        whenever(authConfig.clientSecret).thenReturn("test-secret")
        whenever(loginService.loginM2m(any())).thenReturn(token)
        whenever(loginService.retryOnJwtExpiry<Unit>(any())).thenAnswer { invocation ->
            val block = invocation.getArgument<() -> Unit>(0)
            block()
        }
    }

    @Test
    fun `loadNewEvents should authenticate and call eventLoader`() {
        // Given
        setupAuth()
        whenever(dateUtils.today()).thenReturn("2026-04-14")

        // When
        eventSchedule.loadNewEvents()

        // Then
        verify(loginService).retryOnJwtExpiry<Unit>(any())
        verify(eventLoader).loadEvents("2026-04-14")
    }

    @Test
    fun `loadNewEvents should skip when loginService is null`() {
        // Given: no login service
        val scheduleWithoutLogin = EventSchedule(eventService, eventLoader, dateUtils)

        // When
        scheduleWithoutLogin.loadNewEvents()

        // Then: eventLoader should not be called
        verify(eventLoader, never()).loadEvents(any())
    }
}