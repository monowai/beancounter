package com.beancounter.position.accumulation;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.position.utils.CurrencyResolver;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class DividendBehaviour implements AccumulationStrategy {
  private final CurrencyResolver currencyResolver = new CurrencyResolver();

  public void accumulate(Trn trn, Portfolio portfolio, Position position) {
    value(trn, portfolio, position, Position.In.TRADE, BigDecimal.ONE);
    value(trn, portfolio, position, Position.In.BASE, trn.getTradeBaseRate());
    value(trn, portfolio, position, Position.In.PORTFOLIO,
        trn.getTradePortfolioRate());
  }

  private void value(Trn trn,
                     Portfolio portfolio, Position position,
                     Position.In in,
                     BigDecimal rate) {

    MoneyValues moneyValues = position.getMoneyValues(
        in,
        currencyResolver.resolve(in, portfolio, trn)
    );
    moneyValues.setDividends(
        MathUtils.add(moneyValues.getDividends(),
            MathUtils.divide(trn.getTradeAmount(), rate)));

  }

}
