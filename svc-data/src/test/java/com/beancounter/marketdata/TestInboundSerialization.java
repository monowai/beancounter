package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.input.TrustedTrnImportRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

public class TestInboundSerialization {
  @Test
  void is_InboundPayloadConverted() throws Exception {
    ObjectMapper om = new ObjectMapper();
    TrustedTrnImportRequest payload = om.readValue(
        new ClassPathResource("/kafka/bc-view-send.json").getFile(),
        TrustedTrnImportRequest.class);

    assertThat(payload).isNotNull();

    HashMap<String,Object> message = om.readValue(
        new ClassPathResource("/kafka/csv-import-message.json").getFile(),
        new TypeReference<>() {
        }
    );

    assertThat(message).hasFieldOrProperty("payload");
    TrustedTrnImportRequest fromMsg = om.readValue(
        message.get("payload").toString(),
        TrustedTrnImportRequest.class);

    assertThat(fromMsg).isEqualToComparingFieldByField(payload);
  }
}
