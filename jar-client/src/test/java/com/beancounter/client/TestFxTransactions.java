package com.beancounter.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.ingest.FxTransactions;
import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.CallerRef;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.utils.CurrencyUtils;
import com.beancounter.common.utils.DateUtils;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

public class TestFxTransactions {

  @MockBean
  private FxService fxService;

  @Test
  void is_TrnDefaultsSetting() {
    IsoCurrencyPair tradeBase = new IsoCurrencyPair("USD", "NZD");
    IsoCurrencyPair tradePf = new IsoCurrencyPair("USD", "NZD");
    IsoCurrencyPair tradeCash = new IsoCurrencyPair("USD", "NZD");
    Map<IsoCurrencyPair, FxRate> mapRates = new HashMap<>();
    Currency temp = new CurrencyUtils().getCurrency("TEMP");
    FxRate one = new FxRate(temp, temp, BigDecimal.ONE, null);
    mapRates.put(tradeBase, one);
    mapRates.put(tradePf, one);
    mapRates.put(tradeCash, one);

    FxPairResults pairResults = new FxPairResults();
    pairResults.setRates(mapRates);

    FxRequest fxRequest = new FxRequest();
    fxRequest.addTradeBase(tradeBase);
    fxRequest.addTradePf(tradePf);
    fxRequest.addTradeCash(tradeCash);
    TrnInput trnInput = new TrnInput(new CallerRef(), "ABC");
    FxTransactions fxTransactions = new FxTransactions(fxService, new DateUtils());
    fxTransactions.setRates(pairResults, fxRequest, trnInput);
    assertThat(trnInput)
        .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal.ONE)
        .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal.ONE)
        .hasFieldOrPropertyWithValue("tradePortfolioRate", BigDecimal.ONE);
  }
}
