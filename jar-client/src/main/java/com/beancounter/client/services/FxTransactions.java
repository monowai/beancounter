package com.beancounter.client.services;

import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.CurrencyUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FxTransactions {
  private FxRateService fxRateService;
  private DateUtils dateUtils;

  FxTransactions(FxRateService fxRateService, DateUtils dateUtils) {
    this.fxRateService = fxRateService;
    this.dateUtils = dateUtils;
  }

  public TrnInput applyRates(Portfolio portfolio, TrnInput trn) {
    return applyRates(portfolio, Collections.singleton(trn)).iterator().next();
  }

  public Collection<TrnInput> applyRates(Portfolio portfolio,
                                         Collection<TrnInput> trns) {
    Map<String, FxRequest> fxRequestMap = new HashMap<>();
    for (TrnInput trn : trns) {
      String tradeDate = dateUtils.getDateString(trn.getTradeDate());

      FxRequest fxRequest = getFxRequest(fxRequestMap, tradeDate);

      IsoCurrencyPair tradePf = pair(trn, trn.getTradePortfolioRate(), portfolio.getCurrency());
      fxRequest.add(tradePf);

      IsoCurrencyPair tradeBase = pair(trn, trn.getTradeBaseRate(), portfolio.getBase());
      fxRequest.add(tradeBase);

      IsoCurrencyPair tradeCash = pair(trn, trn.getTradeCashRate(), get(trn.getCashCurrency()));
      fxRequest.add(tradeCash);

      FxResponse fxResponse = fxRateService.getRates(fxRequest);
      applyRates(fxResponse.getData(), tradePf, tradeBase, tradeCash, trn);
    }
    return trns;
  }

  private void applyRates(FxPairResults rates,
                          IsoCurrencyPair tradePortfolio,
                          IsoCurrencyPair tradeBase,
                          IsoCurrencyPair tradeCash,
                          TrnInput trn) {

    if (tradePortfolio != null && MathUtils.isUnset(trn.getTradePortfolioRate())) {
      trn.setTradePortfolioRate(rates.getRates().get(tradePortfolio).getRate());
    } else {
      trn.setTradePortfolioRate(FxRate.ONE.getRate());
    }
    if (tradeBase != null && MathUtils.isUnset(trn.getTradeBaseRate())) {
      trn.setTradeBaseRate(rates.getRates().get(tradeBase).getRate());
    } else {
      trn.setTradeBaseRate(FxRate.ONE.getRate());
    }
    if (tradeCash != null && MathUtils.isUnset(trn.getTradeCashRate())) {
      trn.setTradeCashRate(rates.getRates().get(tradeCash).getRate());
    } else {
      trn.setTradeCashRate(FxRate.ONE.getRate());
    }
  }

  private IsoCurrencyPair pair(TrnInput trn, BigDecimal tradePortfolioRate, Currency currency) {
    return CurrencyUtils.getCurrencyPair(
        tradePortfolioRate,
        get(trn.getTradeCurrency()),
        currency);
  }

  private Currency get(String currency) {
    if (currency == null) {
      return null;
    }
    return CurrencyUtils.getCurrency(currency);
  }

  private FxRequest getFxRequest(Map<String, FxRequest> fxRequests, String tradeDate) {
    FxRequest fxRequest = fxRequests.get(tradeDate);

    if (fxRequest == null) {
      fxRequest = FxRequest.builder()
          .rateDate(tradeDate)
          .build();
      fxRequests.put(tradeDate, fxRequest);
    }
    return fxRequest;
  }

}
