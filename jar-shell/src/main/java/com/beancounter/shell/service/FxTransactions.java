package com.beancounter.shell.service;

import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
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

  @Autowired
  void setFxRateService(FxRateService fxRateService) {
    this.fxRateService = fxRateService;
  }

  public Trn applyRates(Portfolio portfolio, Trn trn) {
    return applyRates(portfolio, Collections.singleton(trn)).iterator().next();
  }

  public Collection<Trn> applyRates(Portfolio portfolio,
                                    Collection<Trn> trns) {
    Map<String, FxRequest> fxRequestMap = new HashMap<>();
    for (Trn trn : trns) {
      String tradeDate = DateUtils.getDateString(trn.getTradeDate());

      FxRequest fxRequest = getFxRequest(fxRequestMap, tradeDate);

      CurrencyPair tradePortfolio = CurrencyUtils.getCurrencyPair(
          trn.getTradePortfolioRate(),
          trn.getAsset().getMarket().getCurrency(),
          portfolio.getCurrency());

      fxRequest.add(tradePortfolio);

      CurrencyPair tradeBase = CurrencyUtils.getCurrencyPair(
          trn.getTradeBaseRate(),
          trn.getAsset().getMarket().getCurrency(),
          portfolio.getBase());
      fxRequest.add(tradeBase);

      CurrencyPair tradeCash = CurrencyUtils.getCurrencyPair(
          trn.getTradeCashRate(),
          trn.getAsset().getMarket().getCurrency(),
          trn.getCashCurrency());

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

  private void applyRates(FxPairResults rates,
                          CurrencyPair tradePortfolio,
                          CurrencyPair tradeBase,
                          CurrencyPair tradeCash,
                          Trn trn) {

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
