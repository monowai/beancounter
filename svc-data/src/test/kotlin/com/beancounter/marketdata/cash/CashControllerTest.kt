package com.beancounter.marketdata.cash

import com.beancounter.auth.MockAuthConfig
import com.beancounter.marketdata.SpringMvcDbTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Test the Cash Controller with security.
 */
@SpringMvcDbTest
class CashControllerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var cashService: CashService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @BeforeEach
    fun setUp() {
        `when`(cashService.find()).thenReturn(emptyList())
    }

    @Test
    fun `should return cash assets securely`() {
        mockMvc
            .perform(
                get("/cash").with(
                    SecurityMockMvcRequestPostProcessors
                        .jwt()
                        .jwt(mockAuthConfig.getUserToken())
                )
            ).andExpect(status().isOk)
    }
}