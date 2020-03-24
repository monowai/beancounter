package com.beancounter.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.shell.sharesight.ShareSightService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

public class TestShareSightService {
  @Test
  void is_InvalidDouble() throws Exception {
    ShareSightService shareSightService = new ShareSightService(null, null);
    assertThat(shareSightService.parseDouble(null)).isNull();
    assertThat(shareSightService.parseDouble("")).isEqualTo(BigDecimal.ZERO);
    assertThat(shareSightService.parseDouble("1,1000.99")).isEqualTo(new BigDecimal("11000.99"));
    assertThat(shareSightService.parseDouble("1,1000")).isEqualTo(new BigDecimal("11000"));
  }
}
