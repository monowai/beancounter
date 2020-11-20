package com.beancounter.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.utils.BcJson;
import com.beancounter.shell.ingest.IngestionRequest;
import org.junit.jupiter.api.Test;

class IngestionRequestTest {

  @Test
  void is_SerializationWorking() throws Exception {

    IngestionRequest ingestionRequest = IngestionRequest.builder()
        .filter("TWEE")
        .portfolioCode("Test")
        .provider("TheProvider")
        .file("123")
        .build();

    assertThat(ingestionRequest.isRatesIgnored()).isTrue();

    String json = BcJson.getObjectMapper().writeValueAsString(ingestionRequest);
    assertThat(BcJson.getObjectMapper().readValue(json, IngestionRequest.class))
        .usingRecursiveComparison().isEqualTo(ingestionRequest);

  }
}
