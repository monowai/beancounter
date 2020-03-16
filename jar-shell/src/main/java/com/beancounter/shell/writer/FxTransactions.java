package com.beancounter.shell.writer;

import com.beancounter.client.FxRateService;
import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.CurrencyUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.MathUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FxTransactions {
  private FxRateService fxRateService;
  private DateUtils dateUtils;

  @Autowired
  void setDateUtils(DateUtils dateUtils) {
    this.dateUtils = dateUtils;
  }

  @Autowired
  void setFxRateService(FxRateService fxRateService) {
    this.fxRateService = fxRateService;
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

      IsoCurrencyPair tradePortfolio = CurrencyUtils.getCurrencyPair(
          trn.getTradePortfolioRate(),
          getOrNull(trn.getTradeCurrency()),
          portfolio.getCurrency());

      fxRequest.add(tradePortfolio);

      IsoCurrencyPair tradeBase = CurrencyUtils.getCurrencyPair(
          trn.getTradeBaseRate(),
          getOrNull(trn.getTradeCurrency()),
          portfolio.getBase());
      fxRequest.add(tradeBase);

      IsoCurrencyPair tradeCash = CurrencyUtils.getCurrencyPair(
          trn.getTradeCashRate(),
          getOrNull(trn.getTradeCurrency()),
          getOrNull(trn.getCashCurrency()));

      fxRequest.add(tradeCash);

      FxResponse fxResponse = fxRateService.getRates(fxRequest);
      if (fxResponse == null) {
        throw new BusinessException(String.format(
            "Unable to obtain rates %s", fxRequest.toString()));
      }
      applyRates(fxResponse.getData(),
          tradePortfolio,
          tradeBase,
          tradeCash,
          trn);
    }
    return trns;
  }

  private Currency getOrNull(String currency) {
    if ( currency == null ) {
      return null;
    }
    return CurrencyUtils.getCurrency(currency);
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
