package com.beancounter.event.corporate

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.event.distribution.EventDistribution
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * HTTP contract of the corporate event endpoints: request validation and status codes.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockAuth
@AutoConfigureMockMvc
class CorporateEventEndpointTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var eventDistribution: EventDistribution

    @Autowired
    private lateinit var eventStore: CorporateEventStore

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Test
    fun `should return 400 without distributing when reprocess payDate is malformed`() {
        val event = eventStore.save(TestHelpers.createTestCorporateEvent(assetCode = "reprocess-bad-date"))

        val response =
            mockMvc
                .perform(
                    post("/{eventId}", event.id)
                        .param("payDate", "not-a-date")
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                ).andExpect(status().isBadRequest)
                .andReturn()
                .response

        assertThat(response.contentAsString).contains("not-a-date")
        verifyNoInteractions(eventDistribution)
    }
}