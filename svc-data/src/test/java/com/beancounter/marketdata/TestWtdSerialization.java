package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Market;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.providers.wtd.WtdConfig;
import com.beancounter.marketdata.providers.wtd.WtdResponse;
import com.beancounter.marketdata.utils.WtdMockUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Market Data integration with AlphaVantage.co
 *
 * @author mikeh
 * @since 2019-03-03
 */
class TestWtdSerialization {
  private ObjectMapper mapper = new ObjectMapper();

  @Test
  void is_JsonGoodResponse() throws Exception {


    File jsonFile = new ClassPathResource(WtdMockUtils.WTD_PATH + "/AAPL-MSFT.json").getFile();
    WtdResponse response = mapper.readValue(jsonFile, WtdResponse.class);

    ZonedDateTime compareTo = ZonedDateTime.of(
        LocalDate.parse("2019-03-08").atStartOfDay(), ZoneId.of("UTC")
    );

    assertThat(response)
        .isNotNull()
        .hasFieldOrPropertyWithValue("date", DateUtils.getDateString(compareTo.toLocalDate()))
        .hasFieldOrProperty("data");
  }

  @Test
  void is_JsonResponseWithMessage() throws Exception {

    File jsonFile = new ClassPathResource(WtdMockUtils.WTD_PATH + "/NoData.json").getFile();
    WtdResponse response = mapper.readValue(jsonFile, WtdResponse.class);

    assertThat(response)
        .isNotNull()
        .hasFieldOrProperty("message");
  }

  @Test
  void is_NzxValuationDateCorrect() {
    WtdConfig wtdConfig = new WtdConfig();

    // Overriding today, so should just return today
    assertThat(wtdConfig.getMarketDate(
        Market.builder().code("NZX").build(), "2019-11-15"))
        .isEqualTo("2019-11-15");

    // If it's Saturday, count back to Friday
    assertThat(wtdConfig.getMarketDate(
        Market.builder().code("NZX").build(), "2019-11-17"))
        .isEqualTo("2019-11-15");

  }

}
