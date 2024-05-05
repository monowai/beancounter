package com.beancounter.event.controller

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.Constants.Companion.ALPHA
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventResponses
import com.beancounter.event.service.EventService
import com.beancounter.event.service.PositionService
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.util.Objects

/**
 * MVC tests for Corporate Actions/Events.
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@AutoConfigureMockAuth
@AutoConfigureMockMvc
internal class EventControllerTest {
    private val dateUtils = DateUtils()

    @Autowired
    private lateinit var wac: WebApplicationContext

    @Autowired
    private lateinit var eventService: EventService

    @Autowired
    private lateinit var positionService: PositionService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    lateinit var token: Jwt

    @BeforeEach
    fun setDefaultUser() {
        token = mockAuthConfig.getUserToken()
    }

    @Test
    fun validateBadRequestResponse() {
        // Execute the request and directly retrieve and parse the response body
        val message =
            mockMvc.perform(
                MockMvcRequestBuilders.get("/{eventId}", "event.getId()")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)),
            )
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn()
                .response
                .contentAsString
                .let { responseBody ->
                    objectMapper.readValue<ProblemDetail>(responseBody)
                }

        // Assert that the response message object has no null fields or properties
        assertThat(message)
            .hasNoNullFieldsOrPropertiesExcept("properties")
    }

    @Test
    fun is_EventCreatedAndFoundOk() {
        // Create
        var event =
            CorporateEvent(
                trnType = TrnType.DIVI,
                recordDate = dateUtils.getFormattedDate("2020-10-10"),
                source = "SOURCE",
                assetId = "ABC123",
                rate = BigDecimal("0.1234"),
            )
        event = eventService.save(event)
        assertThat(event).hasFieldOrProperty("id")

        // Find By event.id
        val eventResponse = eventsById(event)
        eventResponse.shouldNotBeNull()
        assertThat(eventResponse.data).usingRecursiveComparison().isEqualTo(event)

        val events = eventsByAssetId(event)
        events.shouldNotBeNull()

        // Ensure the data list contains exactly one item and it matches the expected event
        events.data shouldContainExactly listOf(event)
    }

    private fun eventsById(event: CorporateEvent): CorporateEventResponse? {
        val mvcResult =
            mockMvc.perform(
                MockMvcRequestBuilders.get("/{eventId}", event.id)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        return objectMapper.readValue(
            mvcResult.response.contentAsString,
            CorporateEventResponse::class.java,
        )
    }

    private fun eventsByAssetId(event: CorporateEvent): CorporateEventResponses? {
        val mvcResult =
            mockMvc.perform(
                MockMvcRequestBuilders.get("/asset/{assetId}", event.assetId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val events =
            objectMapper.readValue(
                mvcResult.response.contentAsString,
                CorporateEventResponses::class.java,
            )
        return events
    }

    @Test
    fun is_ServiceBasedDividendCreateAndFindOk() {
        val event =
            CorporateEvent(
                trnType = TrnType.DIVI,
                payDate = Objects.requireNonNull(DateUtils().getFormattedDate("2019-12-20"))!!,
                source = ALPHA,
                assetId = "assetId",
                rate = BigDecimal("2.3400"),
            )
        val saved = eventService.save(event)
        assertThat(saved.id).isNotNull

        // Check that the saved event's ID matches a new save attempt (assuming idempotency)
        val newSaved = eventService.save(event)
        assertThat(newSaved.id).isEqualTo(saved.id)

        assertThat(eventService.forAsset(event.assetId))
            .isNotNull
            .hasSize(1)

        // Check it can be found within a date range
        val events =
            eventService
                .findInRange(
                    event.recordDate.minusDays(2),
                    event.recordDate,
                )
        // Validate the collection has exactly one element and check that element
        assertThat(events).hasSize(1)

        // Validate the size and contents of the retrieved events
        assertThat(events.single())
            .usingRecursiveComparison()
            .isEqualTo(saved)
    }
}
