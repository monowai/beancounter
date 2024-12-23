package com.beancounter.event

import com.beancounter.auth.AutoConfigureMockAuth
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Test Context loads.
 */
@SpringBootTest
@Tag("slow")
@ActiveProfiles("test")
@AutoConfigureMockAuth
internal class EventBootTests {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun contextLoads() {
        Assertions.assertThat(jwtDecoder).isNotNull
    }
}