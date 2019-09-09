package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.marketdata.providers.wtd.WtdResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Market Data integration with AlphaVantage.co
 *
 * @author mikeh
 * @since 2019-03-03
 */
class TestWtdProvider {
  private ObjectMapper mapper = new ObjectMapper();

  @Test
  @VisibleForTesting
  void jsonGoodResponse() throws Exception {


    File jsonFile = new ClassPathResource("wtdMultiAsset.json").getFile();
    WtdResponse response = mapper.readValue(jsonFile, WtdResponse.class);

    ZonedDateTime compareTo = ZonedDateTime.of(
        LocalDate.parse("2019-03-08").atStartOfDay(), ZoneId.of("UTC")
    );

    assertThat(response)
        .isNotNull()
        .hasFieldOrPropertyWithValue("date", Date.from(compareTo.toInstant()))
        .hasFieldOrProperty("data");
  }

  @Test
  @VisibleForTesting
  void jsonMessageResponse() throws Exception {

    File jsonFile = new ClassPathResource("wtdMessage.json").getFile();
    WtdResponse response = mapper.readValue(jsonFile, WtdResponse.class);

    assertThat(response)
        .isNotNull()
        .hasFieldOrProperty("message");
  }
}
