package com.beancounter.position.valuation;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Position;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class Gains {

  public void value(Position position, Position.In in) {

    MoneyValues moneyValues = position.getMoneyValues(in);
    BigDecimal total = position.getQuantityValues().getTotal();
    boolean hasTotal = !BigDecimal.ZERO.equals(total);

    if (hasTotal) {
      // No quantity == no Unrealised Gain
      moneyValues.setUnrealisedGain(moneyValues.getMarketValue()
          .subtract(moneyValues.getCostValue()));
    }

    moneyValues.setTotalGain(moneyValues.getUnrealisedGain()
        .add(moneyValues.getDividends()
            .add(moneyValues.getRealisedGain())));
  }

}
