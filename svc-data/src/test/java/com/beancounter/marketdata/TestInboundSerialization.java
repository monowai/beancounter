package com.beancounter.marketdata;

import static com.beancounter.common.utils.BcJson.getObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.input.TrustedTrnImportRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

public class TestInboundSerialization {
  @Test
  void is_InboundPayloadConverted() throws Exception {
    TrustedTrnImportRequest payload = getObjectMapper().readValue(
        new ClassPathResource("/kafka/bc-view-send.json").getFile(),
        TrustedTrnImportRequest.class);

    assertThat(payload).isNotNull();

    HashMap<String, Object> message = getObjectMapper().readValue(
        new ClassPathResource("/kafka/csv-import-message.json").getFile(),
        new TypeReference<>() {
        }
    );

    assertThat(message).hasFieldOrProperty("payload");
    TrustedTrnImportRequest fromMsg = getObjectMapper().readValue(
        message.get("payload").toString(),
        TrustedTrnImportRequest.class);

    assertThat(fromMsg.getPortfolio()).isEqualToComparingFieldByField(payload.getPortfolio());
    assertThat(fromMsg.getRow()).contains(payload.getRow().toArray(new String[] {}));
    assertThat(fromMsg.getCallerRef()).isNull();
  }

  @Test
  void is_InboundMessagePayloadConverted() throws Exception {
    TrustedTrnImportRequest payload = getObjectMapper().readValue(
        new ClassPathResource("/kafka/bc-view-message.json").getFile(),
        TrustedTrnImportRequest.class);
  }

  @Test
  void is_IncomingTrustedEvent() throws Exception {
    TrustedTrnEvent inbound = getObjectMapper().readValue(
        new ClassPathResource("/kafka/event-incoming.json").getFile(), TrustedTrnEvent.class);
    assertThat(inbound).isNotNull();
  }

}
