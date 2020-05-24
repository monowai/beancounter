package com.beancounter.position;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.service.Accumulator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = Accumulator.class)
class TestAccumulationOfQuantityValues {
  @Autowired
  private Accumulator accumulator;

  @Test
  void is_QuantityPrecision() {
    QuantityValues quantityValues = QuantityValues.builder().build();
    // Null total
    assertThat(quantityValues.getPrecision()).isEqualTo(0);
    quantityValues.setPurchased(new BigDecimal("100.9992"));
    assertThat(quantityValues.getTotal()).isEqualTo("100.9992");
    assertThat(quantityValues.getPrecision()).isEqualTo(3);

    quantityValues.setPurchased(new BigDecimal("100"));
    assertThat(quantityValues.getPrecision()).isEqualTo(0);

    // User defined precision
    quantityValues.setPrecision(40);
    assertThat(quantityValues.getPrecision()).isEqualTo(40);

  }

  @Test
  void is_TotalQuantityCorrect() {
    Portfolio portfolio = getPortfolio("TEST");
    Trn buyTrn = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(getAsset("marketCode", "CODE"))
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Position position = Position.builder()
        .asset(buyTrn.getAsset())
        .build();

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO);

    position = accumulator.accumulate(buyTrn, portfolio, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(100))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

    position = accumulator.accumulate(buyTrn, portfolio, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
        .hasFieldOrPropertyWithValue("sold", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("total", new BigDecimal(200));


    Trn sell = Trn.builder()
        .trnType(TrnType.SELL)
        .asset(buyTrn.getAsset())
        .quantity(new BigDecimal(100)).build();

    position = accumulator.accumulate(sell, portfolio, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("sold", new BigDecimal(-100))
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

    position = accumulator.accumulate(sell, portfolio, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("sold", new BigDecimal(-200))
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(0));

  }
}
