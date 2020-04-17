package com.beancounter.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.ingest.FxTransactions;
import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
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
    FxPairResults pairResults = new FxPairResults();
    IsoCurrencyPair tradeBase = IsoCurrencyPair.builder().from("USD").to("NZD").build();
    IsoCurrencyPair tradePf = IsoCurrencyPair.builder().from("USD").to("NZD").build();
    IsoCurrencyPair tradeCash = IsoCurrencyPair.builder().from("USD").to("NZD").build();
    Map<IsoCurrencyPair, FxRate> mapRates = new HashMap<>();
    mapRates.put(tradeBase, FxRate.ONE);
    mapRates.put(tradePf, FxRate.ONE);
    mapRates.put(tradeCash, FxRate.ONE);

    pairResults.setRates(mapRates);

    FxRequest fxRequest = FxRequest.builder()
        .tradeBase(tradeBase)
        .tradePf(tradePf)
        .tradeCash(tradeCash)
        .build();
    TrnInput trnInput = TrnInput.builder()
        .build();
    FxTransactions fxTransactions = new FxTransactions(fxService, new DateUtils());
    fxTransactions.setRates(pairResults, fxRequest, trnInput);
    assertThat (trnInput)
        .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal.ONE)
        .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal.ONE)
        .hasFieldOrPropertyWithValue("tradePortfolioRate", BigDecimal.ONE);
  }
}
