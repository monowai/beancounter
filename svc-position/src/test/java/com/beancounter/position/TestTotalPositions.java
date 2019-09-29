package com.beancounter.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.position.model.Positions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TestTotalPositions {

  @Test
  @VisibleForTesting
  void total() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    File positionFile = new ClassPathResource("contracts/postion-results.json").getFile();

    Positions positions = mapper.readValue(positionFile, Positions.class);

    assertThat(positions).isNotNull();
    assertThat(positions.getPositions()).isNotNull().hasSize(3);

    // One USD Closed, one AUD Open one USD Open

  }
}
