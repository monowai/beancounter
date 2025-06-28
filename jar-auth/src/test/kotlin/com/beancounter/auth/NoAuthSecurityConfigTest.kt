package com.beancounter.auth

import com.beancounter.auth.server.NoAuthSecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest(classes = [NoAuthSecurityConfig::class, SimpleController::class])
@AutoConfigureMockMvc
@TestPropertySource(properties = ["auth.web=false"])
class NoAuthSecurityConfigTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `all endpoints are permitted when auth web is false`() {
        mockMvc
            .get("/api/swagger-ui/index.html")
            .andExpect { status { isOk() } }
        mockMvc
            .get("/api/any-other-endpoint")
            .andExpect { status { isOk() } }
    }
}

@RestController
class SimpleController {
    @GetMapping("/api/swagger-ui/index.html")
    fun swaggerUi(): String = "swagger-ui"

    @GetMapping("/api/any-other-endpoint")
    fun anyOther(): String = "any-other"
}