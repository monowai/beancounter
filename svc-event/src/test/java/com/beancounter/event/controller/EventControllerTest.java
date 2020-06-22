package com.beancounter.event.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.exception.SpringExceptionMessage;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.event.contract.CorporateEventResponse;
import com.beancounter.event.contract.CorporateEventsResponse;
import com.beancounter.event.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"auth.enabled=false"})
@ActiveProfiles("test")
@Tag("slow")
class EventControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DateUtils dateUtils = new DateUtils();
  @Autowired
  private WebApplicationContext wac;
  @Autowired
  private EventService eventService;

  @Test
  void is_IllegalEventBad() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();

    MvcResult mvcResult = mockMvc.perform(
        get("/{eventId}", "event.getId()")
    ).andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();
    String responseBody = mvcResult.getResponse().getContentAsString();
    SpringExceptionMessage message = objectMapper
        .readValue(responseBody, SpringExceptionMessage.class);
    assertThat(message).hasNoNullFieldsOrProperties();
    assertThat(message.getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void is_EventCreatedAndFoundOk() throws Exception {
    // Create
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    CorporateEvent event = CorporateEvent.builder()
        .id("ID-1")
        .source("EV-TEST")
        .trnType(TrnType.DIVI)
        .recordDate(dateUtils.getDate("2020-10-10"))
        .assetId("abc123")
        .rate(new BigDecimal("0.1234"))
        .split(new BigDecimal("1.0000"))
        .build();

    event = eventService.save(event);
    assertThat(event).hasFieldOrProperty("id");

    // Find By PK
    MvcResult mvcResult = mockMvc.perform(
        get("/{eventId}", event.getId())
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    CorporateEventResponse eventResponse =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(),
            CorporateEventResponse.class);
    assertThat(eventResponse.getData()).isEqualToComparingFieldByField(event);

    mvcResult = mockMvc.perform(
        get("/asset/{assetId}", event.getAssetId())
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();
    CorporateEventsResponse events =
        objectMapper.readValue(mvcResult.getResponse().getContentAsString(),
            CorporateEventsResponse.class);

    assertThat(events).isNotNull();
    assertThat(events.getData()).hasSize(1);
    assertThat(events.getData().iterator().next()).isEqualToComparingFieldByField(event);
  }
}