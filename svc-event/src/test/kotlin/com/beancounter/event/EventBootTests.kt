package com.beancounter.event

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.context.WebApplicationContext

/**
 * Test Context loads.
 */
@SpringBootTest
@Tag("slow")
@ActiveProfiles("test")
@AutoConfigureMockAuth
internal class EventBootTests
    @Autowired
    private constructor(private val context: WebApplicationContext) {
        @Autowired
        private lateinit var mockAuthConfig: MockAuthConfig

        @Test
        fun contextLoads() {
            Assertions.assertThat(context).isNotNull
        }
    }
