package com.beancounter.client.ingest;

import com.beancounter.client.FxService;
import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.CurrencyUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FxTransactions {
  private final FxService fxService;
  private final DateUtils dateUtils;
  private final CurrencyUtils currencyUtils = new CurrencyUtils();

  public FxTransactions(FxService fxClientService, DateUtils dateUtils) {
    this.fxService = fxClientService;
    this.dateUtils = dateUtils;
  }

  public FxRequest buildRequest(Portfolio portfolio, TrnInput trn) {
    Map<String, FxRequest> fxRequestMap = new HashMap<>();
    String tradeDate = dateUtils.getDateString(trn.getTradeDate());

    FxRequest fxRequest = getFxRequest(fxRequestMap, tradeDate);

    fxRequest.addTradePf(
        pair(portfolio.getCurrency(), trn, trn.getTradePortfolioRate())
    );

    fxRequest.addTradeBase(
        pair(portfolio.getBase(), trn, trn.getTradeBaseRate())
    );

    if (trn.getCashCurrency() != null) {
      fxRequest.addTradeCash(
          pair(currencyUtils.getCurrency(trn.getCashCurrency()), trn, trn.getTradeCashRate())
      );
    }

    return fxRequest;
  }

  public void setRates(FxPairResults rates,
                       FxRequest fxRequest,
                       TrnInput trn) {

    if (fxRequest.getTradePf() != null && MathUtils.isUnset(trn.getTradePortfolioRate())) {
      trn.setTradePortfolioRate(rates.getRates().get(fxRequest.getTradePf()).getRate());
    } else {
      trn.setTradePortfolioRate(BigDecimal.ONE);
    }
    if (fxRequest.getTradeBase() != null && MathUtils.isUnset(trn.getTradeBaseRate())) {
      trn.setTradeBaseRate(rates.getRates().get(fxRequest.getTradeBase()).getRate());
    } else {
      trn.setTradeBaseRate(BigDecimal.ONE);
    }
    if (fxRequest.getTradeCash() != null && MathUtils.isUnset(trn.getTradeCashRate())) {
      trn.setTradeCashRate(rates.getRates().get(fxRequest.getTradeCash()).getRate());
    } else {
      trn.setTradeCashRate(BigDecimal.ONE);
    }
  }

  private IsoCurrencyPair pair(Currency currency, TrnInput trn, BigDecimal rate) {
    return currencyUtils.getCurrencyPair(
        rate,
        currencyUtils.getCurrency(trn.getTradeCurrency()),
        currency);
  }

  private FxRequest getFxRequest(Map<String, FxRequest> fxRequests, String tradeDate) {
    FxRequest fxRequest = fxRequests.get(tradeDate);

    if (fxRequest == null) {
      fxRequest = new FxRequest(tradeDate, new ArrayList<>());
      fxRequests.put(tradeDate, fxRequest);
    }
    return fxRequest;
  }

  public void setTrnRates(Portfolio portfolio, TrnInput trnInput) {
    FxRequest fxRequest = buildRequest(portfolio, trnInput);
    FxResponse fxResponse = fxService.getRates(fxRequest);
    setRates(fxResponse.getData(), fxRequest, trnInput);

  }
}
