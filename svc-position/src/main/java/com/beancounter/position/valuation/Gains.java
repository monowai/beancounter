package com.beancounter.position.valuation;

import com.beancounter.common.model.MoneyValues;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class Gains {

  public void value(BigDecimal total, MoneyValues moneyValues) {

    if (!BigDecimal.ZERO.equals(total)) {
      moneyValues.setUnrealisedGain(
          moneyValues.getMarketValue()
              .subtract(moneyValues.getCostValue()));
    }

    moneyValues.setTotalGain(
        moneyValues.getUnrealisedGain()
            .add(moneyValues.getDividends()
                .add(moneyValues.getRealisedGain())));
  }

}
