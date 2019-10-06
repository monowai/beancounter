package com.beancounter.ingest.service;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxPairResults;
import com.beancounter.common.model.FxResults;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.request.FxRequest;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FxTransactions {
  private FxRateService fxRateService;
  private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

  @Autowired
  void setFxRateService(FxRateService fxRateService) {
    this.fxRateService = fxRateService;
  }

  public Transaction applyRates(Transaction transaction) {

    String tradeDate = simpleDateFormat.format(transaction.getTradeDate());
    FxRequest fxRequest = FxRequest.builder()
        .rateDate(tradeDate)
        .build();

    CurrencyPair tradeBase = getCurrencyPair(
        transaction.getTradeBaseRate(),
        transaction.getBaseCurrency(),
        transaction.getPortfolio().getCurrency());

    fxRequest.add(tradeBase);

    CurrencyPair tradeCash = getCurrencyPair(
        transaction.getTradeCashRate(),
        transaction.getTradeCurrency(),
        transaction.getCashCurrency());

    fxRequest.add(tradeCash);

    CurrencyPair tradeRef = getCurrencyPair(
        transaction.getTradeRefRate(),
        transaction.getTradeCurrency(),
        transaction.getPortfolio().getCurrency());

    fxRequest.add(tradeRef);

    FxResults fxResults = fxRateService.getRates(fxRequest);
    FxPairResults rates = fxResults.getData().get(tradeDate);

    if (tradeRef != null) {
      transaction.setTradeRefRate(rates.getRates().get(tradeRef).getRate());
    }
    if (tradeCash != null) {
      transaction.setTradeCashRate(rates.getRates().get(tradeCash).getRate());
    }
    if (tradeBase != null) {
      transaction.setTradeBaseRate(rates.getRates().get(tradeBase).getRate());
    }

    return transaction;
  }

  private CurrencyPair getCurrencyPair(BigDecimal rate, Currency from, Currency to) {
    CurrencyPair currencyPair = null;
    if (rate == null) {
      currencyPair = CurrencyPair.builder()
          .from(from.getCode())
          .to(to.getCode())
          .build();
    }
    return currencyPair;
  }
}
