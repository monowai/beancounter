package com.beancounter.ingest.service;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxPairResults;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.utils.MathUtils;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FxTransactions {
  private FxRateService fxRateService;
  private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
  private MathUtils mathUtils = new MathUtils();

  @Autowired
  @VisibleForTesting
  void setFxRateService(FxRateService fxRateService) {
    this.fxRateService = fxRateService;
  }

  public Transaction applyRates(Transaction transaction) {
    return applyRates(Collections.singleton(transaction)).iterator().next();
  }

  public Collection<Transaction> applyRates(Collection<Transaction> transactions) {
    Map<String, FxRequest> fxRequestMap = new HashMap<>();
    for (Transaction transaction : transactions) {
      String tradeDate = simpleDateFormat.format(transaction.getTradeDate());

      FxRequest fxRequest = getFxRequest(fxRequestMap, tradeDate);

      CurrencyPair tradePortfolio = getCurrencyPair(
          transaction.getTradePortfolioRate(),
          transaction.getTradeCurrency(),
          transaction.getPortfolio().getCurrency());

      fxRequest.add(tradePortfolio);

      CurrencyPair tradeBase = getCurrencyPair(
          transaction.getTradeBaseRate(),
          transaction.getTradeCurrency(),
          transaction.getBaseCurrency());
      fxRequest.add(tradeBase);

      CurrencyPair tradeCash = getCurrencyPair(
          transaction.getTradeCashRate(),
          transaction.getTradeCurrency(),
          transaction.getCashCurrency());

      fxRequest.add(tradeCash);

      FxResponse fxResponse = fxRateService.getRates(fxRequest);

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

    if (tradePortfolio != null && mathUtils.isUnset(transaction.getTradePortfolioRate())) {
      transaction.setTradePortfolioRate(rates.getRates().get(tradePortfolio).getRate());
    } else {
      transaction.setTradePortfolioRate(BigDecimal.ONE);
    }
    if (tradeBase != null && mathUtils.isUnset(transaction.getTradeBaseRate())) {
      transaction.setTradeBaseRate(rates.getRates().get(tradeBase).getRate());
    } else {
      transaction.setTradeBaseRate(BigDecimal.ONE);
    }
    if (tradeCash != null && mathUtils.isUnset(transaction.getTradeCashRate())) {
      transaction.setTradeCashRate(rates.getRates().get(tradeCash).getRate());
    } else {
      transaction.setTradeCashRate(BigDecimal.ONE);
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

  private CurrencyPair getCurrencyPair(BigDecimal rate, Currency from, Currency to) {
    CurrencyPair currencyPair = null;
    if (from == null || to == null) {
      return null;
    }

    if (rate == null && !from.getCode().equalsIgnoreCase(to.getCode())) {
      currencyPair = CurrencyPair.builder()
          .from(from.getCode())
          .to(to.getCode())
          .build();
    }
    return currencyPair;
  }
}
