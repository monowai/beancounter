package com.beancounter.shell;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.shell.sharesight.ShareSightService;
import com.beancounter.shell.sharesight.ShareSightTradeAdapter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

public class TestAdapters {
  @MockBean
  private ShareSightService shareSightService;

  @Test
  void is_NullTrnTypeCorrect() {

    List<String> nullTrnType = new ArrayList<>();
    nullTrnType.add("");
    nullTrnType.add("");
    nullTrnType.add(null);
    nullTrnType.add("");

    ShareSightTradeAdapter tradeAdapter = new ShareSightTradeAdapter(shareSightService);
    assertThrows(BusinessException.class, ()
        -> tradeAdapter.from(nullTrnType, PortfolioUtils.getPortfolio("TEST")));

  }

  @Test
  void is_BlankTrnTypeCorrect() {

    List<String> nullTrnType = new ArrayList<>();
    nullTrnType.add("");
    nullTrnType.add("");
    nullTrnType.add("");
    nullTrnType.add("");

    ShareSightTradeAdapter tradeAdapter = new ShareSightTradeAdapter(shareSightService);
    assertThrows(BusinessException.class, ()
        -> tradeAdapter.from(nullTrnType, PortfolioUtils.getPortfolio("TEST")));

  }

}
