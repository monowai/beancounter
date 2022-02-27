package com.beancounter.event.controller

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.SpringExceptionMessage
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.Constants.Companion.alpha
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventResponses
import com.beancounter.event.service.EventService
import com.beancounter.event.service.PositionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
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

    private val objectMapper: ObjectMapper = BcJson().objectMapper
    lateinit var token: Jwt
    @Autowired
    fun setDefaultUser() {
        token = mockAuthConfig.getUserToken()
    }

    @Test
    fun is_IllegalEventBad() {
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get("/{eventId}", "event.getId()")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val responseBody = mvcResult.response.contentAsString
        val message = objectMapper
            .readValue(responseBody, SpringExceptionMessage::class.java)
        assertThat(message).hasNoNullFieldsOrProperties()
    }

    @Test
    fun is_EventCreatedAndFoundOk() {
        // Create
        var event = CorporateEvent(
            TrnType.DIVI,
            dateUtils.getDate("2020-10-10"),
            "SOURCE",
            "ABC123",
            BigDecimal("0.1234")
        )
        event = eventService.save(event)
        assertThat(event).hasFieldOrProperty("id")

        // Find By PK
        var mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get("/{eventId}", event.id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper.readValue(
            mvcResult.response.contentAsString,
            CorporateEventResponse::class.java
        )
        assertThat(data).usingRecursiveComparison().isEqualTo(event)
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get("/asset/{assetId}", event.assetId)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val events = objectMapper.readValue(
            mvcResult.response.contentAsString,
            CorporateEventResponses::class.java
        )
        assertThat(events).isNotNull
        assertThat(events.data).hasSize(1)
        assertThat(events.data.iterator().next()).usingRecursiveComparison().isEqualTo(event)
    }

    @Test
    fun is_ServiceBasedDividendCreateAndFindOk() {
        val event = CorporateEvent(
            TrnType.DIVI,
            Objects.requireNonNull(DateUtils().getDate("2019-12-20"))!!,
            alpha,
            "assetId",
            BigDecimal("2.3400")
        )
        val saved = eventService.save(event)
        assertThat(saved.id).isNotNull
        val (id) = eventService.save(event)
        assertThat(id).isEqualTo(saved.id)
        assertThat(eventService.forAsset(event.assetId))
            .isNotNull
            .hasSize(1)

        // Check it can be found within a date range
        val events = eventService
            .findInRange(
                Objects.requireNonNull(event.recordDate).minusDays(2),
                event.recordDate
            )
        assertThat(events).hasSize(1)
        assertThat(events.iterator().next()).usingRecursiveComparison().isEqualTo(saved)
    }
}
