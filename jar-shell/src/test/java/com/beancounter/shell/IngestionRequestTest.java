package com.beancounter.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.shell.ingest.IngestionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class IngestionRequestTest {

  @Test
  void is_SerializationWorking() throws Exception {

    IngestionRequest ingestionRequest = IngestionRequest.builder()
        .filter("TWEE")
        .portfolioCode("Test")
        .provider("TheProvider")
        .sheetId("123")
        .build();

    assertThat(ingestionRequest.isRatesIgnored()).isTrue();

    ObjectMapper objectMapper = new ObjectMapper();
    String json = objectMapper.writeValueAsString(ingestionRequest);
    assertThat(objectMapper.readValue(json, IngestionRequest.class))
        .isEqualToComparingFieldByField(ingestionRequest);

  }
}
