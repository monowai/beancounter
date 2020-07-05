package com.beancounter.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.position.service.Accumulator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test rules that would prevent a transaction from accumulating.
 */
@SpringBootTest(classes = Accumulator.class)
class TestTrnLogic {
  @Autowired
  private Accumulator accumulator;

  /**
   * Transactions should be ordered.  If the date is ==, then it will be accepted but
   * unordered transactions will result in an Exception being thrown
   */
  @Test
  void do_UnorderedTransactionsError() {
    Asset apple = AssetUtils.getAsset("NASDAQ", "AAPL");
    Positions positions = new Positions(PortfolioUtils.getPortfolio("TEST"));

    Position position = positions.get(apple);

    assertThat(position)
        .isNotNull();

    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minus(-1, ChronoUnit.DAYS);

    Trn buyYesterday = new Trn(TrnType.BUY, apple);
    buyYesterday.setTradeAmount(new BigDecimal(2000));
    buyYesterday.setTradeDate(new DateUtils().convert(yesterday));
    buyYesterday.setQuantity(new BigDecimal(100));

    Trn buyToday = new Trn(TrnType.BUY, apple);
    buyToday.setTradeAmount(new BigDecimal(2000));
    buyToday.setTradeDate(new DateUtils().convert(today));
    buyToday.setQuantity(new BigDecimal(100));

    positions.add(position);
    position = accumulator.accumulate(buyYesterday, positions.getPortfolio(), position);

    Position finalPosition = position;
    assertThrows(BusinessException.class, () ->
        accumulator.accumulate(buyToday, positions.getPortfolio(), finalPosition));


  }
}
