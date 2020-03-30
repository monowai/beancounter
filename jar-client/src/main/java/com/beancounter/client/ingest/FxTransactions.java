package com.beancounter.client.ingest;

import com.beancounter.client.FxService;
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
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FxTransactions {
  private FxService fxService;
  private DateUtils dateUtils;

  FxTransactions(FxService fxService, DateUtils dateUtils) {
    this.fxService = fxService;
    this.dateUtils = dateUtils;
  }

  public FxRequest buildRequest(Portfolio portfolio, TrnInput trn) {
    Map<String, FxRequest> fxRequestMap = new HashMap<>();
    String tradeDate = dateUtils.getDateString(trn.getTradeDate());

    FxRequest fxRequest = getFxRequest(fxRequestMap, tradeDate);

    fxRequest.setTradePf(
        pair(trn, trn.getTradePortfolioRate(), portfolio.getCurrency())
    );

    fxRequest.setTradeBase(
        pair(trn, trn.getTradeBaseRate(), portfolio.getBase())
    );

    fxRequest.setTradeCash(
        pair(trn, trn.getTradeCashRate(), get(trn.getCashCurrency()))
    );

    return fxRequest;
  }

  public void setRates(FxPairResults rates,
                       FxRequest fxRequest,
                       TrnInput trn) {

    if (fxRequest.getTradePf() != null && MathUtils.isUnset(trn.getTradePortfolioRate())) {
      trn.setTradePortfolioRate(rates.getRates().get(fxRequest.getTradePf()).getRate());
    } else {
      trn.setTradePortfolioRate(FxRate.ONE.getRate());
    }
    if (fxRequest.getTradeBase() != null && MathUtils.isUnset(trn.getTradeBaseRate())) {
      trn.setTradeBaseRate(rates.getRates().get(fxRequest.getTradeBase()).getRate());
    } else {
      trn.setTradeBaseRate(FxRate.ONE.getRate());
    }
    if (fxRequest.getTradeCash() != null && MathUtils.isUnset(trn.getTradeCashRate())) {
      trn.setTradeCashRate(rates.getRates().get(fxRequest.getTradeCash()).getRate());
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

  public void setTrnRates(Portfolio portfolio, TrnInput trnInput) {
    FxRequest fxRequest = buildRequest(portfolio, trnInput);
    FxResponse fxResponse = fxService.getRates(fxRequest);
    setRates(fxResponse.getData(), fxRequest, trnInput);

  }
}
