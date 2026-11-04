package com.beancounter.event.corporate

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.backfill.BackFillService
import com.beancounter.event.backfill.EventLoader
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

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

    @MockitoBean
    private lateinit var backFillService: BackFillService

    @MockitoBean
    private lateinit var eventLoader: EventLoader

    @MockitoBean
    private lateinit var portfolioService: PortfolioServiceClient

    @Autowired
    private lateinit var dateUtils: DateUtils

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

    @Test
    fun `should return 400 when only one date bound is given`() {
        listOf("fromDate", "toDate").forEach { bound ->
            mockMvc
                .perform(
                    get("/events/{assetId}", "one-bound-asset")
                        .param(bound, "2024-01-01")
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                ).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `should return only the asset's events inside the range`() {
        val assetId = "ranged-asset"
        val inside = eventStore.save(rangeEvent(assetId, "2024-01-15"))
        eventStore.save(rangeEvent(assetId, "2024-03-15"))
        eventStore.save(rangeEvent("other-ranged-asset", "2024-01-15"))

        val body =
            mockMvc
                .perform(
                    get("/events/{assetId}", assetId)
                        .param("fromDate", "2024-01-01")
                        .param("toDate", "2024-01-31")
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                ).andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val events = objectMapper.readValue(body, CorporateEventResponses::class.java).data
        assertThat(events.map { it.id }).containsExactly(inside.id)
    }

    private fun rangeEvent(
        assetId: String,
        recordDate: String
    ) = TestHelpers.createTestCorporateEvent(assetCode = assetId, recordDate = LocalDate.parse(recordDate))

    @Test
    fun `should return 200 once a portfolio backfill has run`() {
        mockMvc
            .perform(
                post("/backfill/{portfolioId}", "sync-portfolio")
                    .with(jwt().jwt(mockAuthConfig.getUserToken()))
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
    }

    @Test
    fun `should return 200 once a portfolio load has run`() {
        mockMvc
            .perform(
                post("/load/{portfolioId}", "sync-portfolio")
                    .with(jwt().jwt(mockAuthConfig.getUserToken()))
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
    }

    @Test
    fun `should return 200 once an event has been reprocessed`() {
        val event = eventStore.save(TestHelpers.createTestCorporateEvent(assetCode = "reprocess-sync"))

        mockMvc
            .perform(
                post("/{eventId}", event.id)
                    .with(jwt().jwt(mockAuthConfig.getUserToken()))
            ).andExpect(status().isOk)
    }

    @Test
    fun `should return 400 without loading when the load date is after today`() {
        val tomorrow = dateUtils.date.plusDays(1).toString()

        mockMvc
            .perform(
                post("/load")
                    .param("fromDate", tomorrow)
                    .with(jwt().jwt(mockAuthConfig.getUserToken()))
            ).andExpect(status().isBadRequest)
        mockMvc
            .perform(
                post("/load/{portfolioId}", "future-portfolio")
                    .param("asAt", tomorrow)
                    .with(jwt().jwt(mockAuthConfig.getUserToken()))
            ).andExpect(status().isBadRequest)

        verifyNoInteractions(eventLoader)
    }
}