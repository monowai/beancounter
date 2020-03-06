package com.beancounter.position.valuation;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MarketValue {
  private Gains gains;

  public MarketValue(Gains gains) {
    this.gains = gains;
  }

  public void value(Positions positions,
                    MarketData marketData,
                    Map<IsoCurrencyPair, FxRate> rates) {

    Asset asset = marketData.getAsset();
    Currency trade = asset.getMarket().getCurrency();
    Position position = positions.get(asset);
    Portfolio portfolio = positions.getPortfolio();

    value(position, marketData, FxRate.ONE, Position.In.TRADE);
    value(position, marketData, rate(portfolio.getBase(), trade, rates), Position.In.BASE);
    value(position, marketData, rate(portfolio.getCurrency(), trade, rates), Position.In.PORTFOLIO);
    position.setAsset(asset);
  }

  private void value(Position position, MarketData marketData, FxRate rate, Position.In in) {
    BigDecimal total = position.getQuantityValues().getTotal();
    MoneyValues moneyValues = position.getMoneyValues(in);
    moneyValues.setPrice(MathUtils.multiply(marketData.getClose(), rate.getRate()));
    moneyValues.setMarketValue(moneyValues.getPrice().multiply(total));
    gains.value(position, in);
  }

  private FxRate rate(Currency report, Currency trade, Map<IsoCurrencyPair, FxRate> rates) {
    if (report.getCode().equals(trade.getCode())) {
      return FxRate.ONE;
    }
    FxRate fxRate = rates.get(IsoCurrencyPair.from(report, trade));
    if (fxRate == null) {
      throw new BusinessException(String.format("No rate for %s:%s",
          report.getCode(),
          trade.getCode()
      ));
    }
    return fxRate;
  }

}
