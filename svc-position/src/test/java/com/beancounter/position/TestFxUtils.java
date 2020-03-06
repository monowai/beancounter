package com.beancounter.position;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.position.utils.FxUtils;
import org.junit.jupiter.api.Test;

class TestFxUtils {
  private FxUtils fxUtils = new FxUtils();

  @Test
  void is_CurrencyPairResultsAsExpected() {

    Position inValidPosition = Position.builder().asset(
        getAsset("Test","TWEE")
    ).build();
    Currency invalidCurrency = inValidPosition.getAsset().getMarket().getCurrency();
    assertThat(IsoCurrencyPair.from(null, invalidCurrency))
        .isNull(); // No currency in market

    Position validPosition = Position.builder().asset(
        getAsset("Test",
            Market.builder()
                .code("USD")
                .currency(getCurrency("USD")).build()
        )
    ).build();
    Currency validCurrency = validPosition.getAsset().getMarket().getCurrency();

    assertThat(IsoCurrencyPair.from(null, validCurrency))
        .isNull(); // From == null

    Currency usd = getCurrency("USD");
    assertThat(IsoCurrencyPair.from(usd, validCurrency))
        .isNull();// From == To

    assertThat(IsoCurrencyPair.from(getCurrency("NZD"), validCurrency))
        .isNotNull();// From != To
  }

  @Test
  void is_FxRequestCorrect() {
    Currency usd = getCurrency("USD");
    Position gbpPosition = Position.builder().asset(
        getAsset("GBP Asset",
            Market.builder()
                .code("GBP")
                .currency(getCurrency("GBP")).build()
        )
    ).build();

    Position usdPosition = Position.builder().asset(
        getAsset("USD Asset",
            Market.builder()
                .code("USD")
                .currency(usd).build()
        )
    ).build();

    Position otherUsdPosition = Position.builder().asset(
        getAsset("USD Asset Other",
            Market.builder()
                .code("USD")
                .currency(usd).build()
        )
    ).build();

    Portfolio portfolio = Portfolio.builder().currency(getCurrency("SGD")).build();
    Positions positions = new Positions(portfolio);
    positions.add(gbpPosition);
    positions.add(usdPosition);
    positions.add(otherUsdPosition);

    FxRequest fxRequest = fxUtils.buildRequest(usd, positions);
    assertThat(fxRequest.getPairs()).hasSize(3)
        .containsOnly(
            IsoCurrencyPair.builder().from("SGD").to("USD").build(), // PF:TRADE
            IsoCurrencyPair.builder().from("SGD").to("GBP").build(), // PF:TRADE
            IsoCurrencyPair.builder().from("USD").to("GBP").build());  // BASE:TRADE
  }
}
