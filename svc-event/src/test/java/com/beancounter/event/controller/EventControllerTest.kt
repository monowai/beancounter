package com.beancounter.event.controller

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.SpringExceptionMessage
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventsResponse
import com.beancounter.event.service.EventService
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

@ExtendWith(SpringExtension::class)
@SpringBootTest(properties = ["auth.enabled=false"])
@ActiveProfiles("test")
@Tag("slow")
internal class EventControllerTest {
    private val dateUtils = DateUtils()

    @Autowired
    private lateinit var wac: WebApplicationContext

    @Autowired
    private lateinit var eventService: EventService

    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Test
    @Throws(Exception::class)
    fun is_IllegalEventBad() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get("/{eventId}", "event.getId()")
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val responseBody = mvcResult.response.contentAsString
        val message = objectMapper
                .readValue(responseBody, SpringExceptionMessage::class.java)
        assertThat(message).hasNoNullFieldsOrProperties()
        assertThat(message.status)
                .isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    @Throws(Exception::class)
    fun is_EventCreatedAndFoundOk() {
        // Create
        val mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
        var event = CorporateEvent(
                TrnType.DIVI,
                "SOURCE",
                "ABC123",
                dateUtils.getDate("2020-10-10")!!,
                BigDecimal("0.1234")
        )
        event = eventService.save(event)
        assertThat(event).hasFieldOrProperty("id")

        // Find By PK
        var mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/{eventId}", event.id)
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper.readValue(
                mvcResult.response.contentAsString,
                CorporateEventResponse::class.java)
        assertThat(data).usingRecursiveComparison().isEqualTo(event)
        mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/asset/{assetId}", event.assetId)
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val events = objectMapper.readValue(mvcResult.response.contentAsString,
                CorporateEventsResponse::class.java)
        assertThat(events).isNotNull
        assertThat(events.data).hasSize(1)
        assertThat(events.data.iterator().next()).usingRecursiveComparison().isEqualTo(event)
    }
}