package com.beancounter.ingest.service;

import com.beancounter.common.helper.MathHelper;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxPairResults;
import com.beancounter.common.model.FxResults;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.request.FxRequest;
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
  private MathHelper mathHelper = new MathHelper();

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

      CurrencyPair tradeCash = getCurrencyPair(
          transaction.getTradeCashRate(),
          transaction.getTradeCurrency(),
          transaction.getCashCurrency());

      fxRequest.add(tradeCash);

      CurrencyPair tradeBase = getCurrencyPair(
          transaction.getTradeBaseRate(),
          transaction.getBaseCurrency(),
          transaction.getPortfolio().getCurrency());
      fxRequest.add(tradeBase);

      CurrencyPair tradeRef = getCurrencyPair(
          transaction.getTradeRefRate(),
          transaction.getTradeCurrency(),
          transaction.getPortfolio().getCurrency());

      fxRequest.add(tradeRef);

      FxResults fxResults = fxRateService.getRates(fxRequest);

      applyRates(fxResults.getData().get(tradeDate),
          tradeCash,
          tradeBase,
          tradeRef,
          transaction);
    }
    return transactions;
  }

  private void applyRates(FxPairResults rates,
                                 CurrencyPair tradeCash,
                                 CurrencyPair tradeBase,
                                 CurrencyPair tradeRef,
                                 Transaction transaction) {

    if (tradeCash != null && mathHelper.isUnset(transaction.getTradeCashRate())) {
      transaction.setTradeCashRate(rates.getRates().get(tradeCash).getRate());
    } else {
      transaction.setTradeCashRate(BigDecimal.ONE);
    }
    if (tradeBase != null && mathHelper.isUnset(transaction.getTradeBaseRate())) {
      transaction.setTradeBaseRate(rates.getRates().get(tradeBase).getRate());
    } else {
      transaction.setTradeBaseRate(BigDecimal.ONE);
    }
    if (tradeRef != null && mathHelper.isUnset(transaction.getTradeRefRate())) {
      transaction.setTradeRefRate(rates.getRates().get(tradeRef).getRate());
    } else {
      transaction.setTradeRefRate(BigDecimal.ONE);
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
