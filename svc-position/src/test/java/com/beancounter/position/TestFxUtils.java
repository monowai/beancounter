package com.beancounter.position;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import com.beancounter.position.utils.FxUtils;
import com.google.common.annotations.VisibleForTesting;
import org.junit.jupiter.api.Test;

class TestFxUtils {
  private FxUtils fxUtils = new FxUtils();

  @VisibleForTesting
  @Test
  void is_CurrencyPairResultsAsExpected() {

    Position inValidPosition = Position.builder().asset(
        getAsset("Test", Market.builder().build())
    ).build();

    assertThat(fxUtils.getPair(null, inValidPosition))
        .isNull(); // No currency in market

    Position validPosition = Position.builder().asset(
        getAsset("Test",
            Market.builder().currency(getCurrency("USD")).build()
        )
    ).build();

    assertThat(fxUtils.getPair(null, validPosition))
        .isNull(); // From == null

    Currency usd = getCurrency("USD");
    assertThat(fxUtils.getPair(usd, validPosition))
        .isNull();// From == To

    assertThat(fxUtils.getPair(getCurrency("NZD"), validPosition))
        .isNotNull();// From != To
  }

  @VisibleForTesting
  @Test
  void is_FxRequestCorrect() {
    Currency usd = getCurrency("USD");
    Position gbpPosition = Position.builder().asset(
        getAsset("GBP Asset",
            Market.builder().currency(getCurrency("GBP")).build()
        )
    ).build();

    Position usdPosition = Position.builder().asset(
        getAsset("USD Asset",
            Market.builder().currency(usd).build()
        )
    ).build();

    Position otherUsdPosition = Position.builder().asset(
        getAsset("USD Asset Other",
            Market.builder().currency(usd).build()
        )
    ).build();

    Portfolio portfolio = Portfolio.builder().currency(getCurrency("SGD")).build();
    Positions positions = new Positions(portfolio);
    positions.add(gbpPosition);
    positions.add(usdPosition);
    positions.add(otherUsdPosition);

    FxRequest fxRequest = fxUtils.getFxRequest(usd, positions);
    assertThat(fxRequest.getPairs()).hasSize(3)
        .containsOnly(
            CurrencyPair.builder().from("SGD").to("USD").build(), // PF:TRADE
            CurrencyPair.builder().from("SGD").to("GBP").build(), // PF:TRADE
            CurrencyPair.builder().from("USD").to("GBP").build());  // BASE:TRADE
  }
}
