package com.beancounter.position.service;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class TotalPositions {

  public Positions calc(Positions positions) {
    BigDecimal refPurchases = BigDecimal.ZERO;
    BigDecimal refSales = BigDecimal.ZERO;
    BigDecimal refDividends = BigDecimal.ZERO;

    for (Position position : positions.getPositions().values()) {
      MoneyValues portfolioValues = position.getMoneyValue(Position.In.PORTFOLIO);
      refPurchases = refPurchases.add(portfolioValues.getPurchases());
      refSales = refSales.add(portfolioValues.getSales());
      refDividends = refDividends.add(portfolioValues.getDividends());
    }
    return positions;
  }
}
