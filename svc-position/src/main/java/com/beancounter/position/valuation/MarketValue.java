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
import com.beancounter.common.model.PriceData;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MarketValue {
  private final Gains gains;

  public MarketValue(Gains gains) {
    this.gains = gains;
  }

  public void value(Positions positions,
                    MarketData marketData,
                    Map<IsoCurrencyPair, FxRate> rates) {

    Asset asset = marketData.getAsset();
    assert asset.getMarket() != null && asset.getMarket().getCurrency() != null;
    Currency trade = asset.getMarket().getCurrency();
    Position position = positions.get(asset);
    Portfolio portfolio = positions.getPortfolio();
    BigDecimal total = position.getQuantityValues().getTotal();

    value(total, position.getMoneyValues(Position.In.TRADE), marketData,
        FxRate.ONE
    );
    value(total, position.getMoneyValues(Position.In.BASE), marketData,
        rate(portfolio.getBase(), trade, rates)
    );
    value(total, position.getMoneyValues(Position.In.PORTFOLIO), marketData,
        rate(portfolio.getCurrency(), trade, rates)
    );
  }

  private void value(BigDecimal total, MoneyValues moneyValues, MarketData mktData, FxRate rate) {

    moneyValues.setPriceData(PriceData.of(mktData, rate.getRate()));
    if (total.compareTo(BigDecimal.ZERO) == 0) {
      moneyValues.setMarketValue(BigDecimal.ZERO);
    } else {
      moneyValues.setMarketValue(MathUtils.multiply(moneyValues.getPriceData().getClose(),total));
    }
    gains.value(total, moneyValues);
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
