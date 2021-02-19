package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.utils.BcJson;
import com.beancounter.marketdata.assets.figi.FigiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

public class FigiSerializationTest {
  @Test
  void is_ResultSerializable() throws Exception {
    Collection<FigiResponse> responses = new BcJson().getObjectMapper().readValue(
        new ClassPathResource("/contracts" + "/figi/multi-asset-response.json").getFile(),
        new TypeReference<>() {
        });

    assertThat(responses).hasSize(2);
  }

}
