package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Market;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.providers.wtd.WtdConfig;
import java.time.LocalDate;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

public class TestWtdConfig {
  private final DateUtils dateUtils = new DateUtils();

  @Test
  void is_DateAssumptionsValid() {
    WtdConfig wtdConfig = new WtdConfig();
    String date = "2020-02-06";
    wtdConfig.setDate(date);
    assertThat(wtdConfig.getDate()).isEqualTo(date);

    String today = dateUtils.today();
    wtdConfig.setDate(today);
    assertThat(wtdConfig.getDate()).isEqualTo(today);

    wtdConfig.setDate(null);
    assertThat(wtdConfig.getDate()).isEqualTo(today);

    // On AsAt the date is equal to the requested date
    Market nzx = Market.builder().code("NZX").build();
    wtdConfig.setDate(date);
    assertThat(wtdConfig.getMarketDate(nzx, date))
        .isEqualTo(date);

    // On Today, it should subtract 2 days
    LocalDate expectedDate = dateUtils.getLastMarketDate(
        dateUtils.getDate(today), TimeZone.getTimeZone("US/Eastern").toZoneId(), 2);
    wtdConfig.setDate(today);
    assertThat(wtdConfig.getMarketDate(nzx, today)).isEqualTo(expectedDate.toString());

  }
}
