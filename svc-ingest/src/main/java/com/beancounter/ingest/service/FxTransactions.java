package com.beancounter.ingest.service;

import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.Transaction;
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

  public Transaction applyRates(Transaction transaction) {
    return applyRates(Collections.singleton(transaction)).iterator().next();
  }

  public Collection<Transaction> applyRates(Collection<Transaction> transactions) {
    Map<String, FxRequest> fxRequestMap = new HashMap<>();
    for (Transaction transaction : transactions) {
      String tradeDate = DateUtils.getDate(transaction.getTradeDate());

      FxRequest fxRequest = getFxRequest(fxRequestMap, tradeDate);

      CurrencyPair tradePortfolio = CurrencyUtils.getCurrencyPair(
          transaction.getTradePortfolioRate(),
          transaction.getAsset().getMarket().getCurrency(),
          transaction.getPortfolio().getCurrency());

      fxRequest.add(tradePortfolio);

      CurrencyPair tradeBase = CurrencyUtils.getCurrencyPair(
          transaction.getTradeBaseRate(),
          transaction.getAsset().getMarket().getCurrency(),
          transaction.getPortfolio().getBase());
      fxRequest.add(tradeBase);

      CurrencyPair tradeCash = CurrencyUtils.getCurrencyPair(
          transaction.getTradeCashRate(),
          transaction.getAsset().getMarket().getCurrency(),
          transaction.getCashCurrency());

      fxRequest.add(tradeCash);

      FxResponse fxResponse = fxRateService.getRates(fxRequest);
      if (fxResponse == null) {
        throw new SystemException(String.format(
            "Unable to obtain rates %s", fxRequest.toString()));
      }
      applyRates(fxResponse.getData(),
          tradePortfolio,
          tradeBase,
          tradeCash,
          transaction);
    }
    return transactions;
  }

  private void applyRates(FxPairResults rates,
                          CurrencyPair tradePortfolio,
                          CurrencyPair tradeBase,
                          CurrencyPair tradeCash,
                          Transaction transaction) {

    if (tradePortfolio != null && MathUtils.isUnset(transaction.getTradePortfolioRate())) {
      transaction.setTradePortfolioRate(rates.getRates().get(tradePortfolio).getRate());
    } else {
      transaction.setTradePortfolioRate(FxRate.ONE.getRate());
    }
    if (tradeBase != null && MathUtils.isUnset(transaction.getTradeBaseRate())) {
      transaction.setTradeBaseRate(rates.getRates().get(tradeBase).getRate());
    } else {
      transaction.setTradeBaseRate(FxRate.ONE.getRate());
    }
    if (tradeCash != null && MathUtils.isUnset(transaction.getTradeCashRate())) {
      transaction.setTradeCashRate(rates.getRates().get(tradeCash).getRate());
    } else {
      transaction.setTradeCashRate(FxRate.ONE.getRate());
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
