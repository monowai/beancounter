package com.beancounter.position.integration;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.position.service.Accumulator;
import com.beancounter.position.service.PositionService;
import com.beancounter.position.service.Valuation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;


/**
 * Market Valuation testing.
 *
 * @author mikeh
 * @since 2019-02-25
 */
@Tag("slow")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-md:+:stubs:8091")
@ActiveProfiles("test")
@SpringBootTest
class TestMarketValues {

  @Autowired
  private Valuation valuation;

  @Autowired
  private PositionService positionService;

  private Positions getValuedPositions(Asset asset, String asAt) {
    Positions positions = getPositions(asset, asAt);
    valuation.value(positions);
    return positions;
  }

  private Positions getPositions(Asset asset, String asAt) {
    // Changing the currency of the portfolio will mean having to regenerate contracts
    Portfolio portfolio = getPortfolio("TEST");
    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(asset)
        .portfolio(portfolio)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    positionService.setObjects(buy);
    Accumulator accumulator = new Accumulator();
    Positions positions = new Positions(portfolio);
    positions.setAsAt(asAt);
    Position position = accumulator.accumulate(buy, Position.builder().asset(asset).build());
    positions.add(position);
    return positions;
  }

  @Test
  @Tag("slow")
  void is_MarketValuationCalculatedAsAt() {
    Asset asset = AssetUtils.getAsset("EBAY",
        Market.builder().code("NASDAQ")
            .currency(getCurrency("USD"))
            .build()
    );

    // We need to have a Quantity in order to get the price, so create a position
    Positions positions = getValuedPositions(asset, "2019-10-18");

    assertThat(positions.get(asset).getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("unrealisedGain", new BigDecimal("8000.00"))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("100.00"))
        .hasFieldOrPropertyWithValue("marketValue", new BigDecimal("10000.00"))
        .hasFieldOrPropertyWithValue("totalGain", new BigDecimal("8000.00"))
    ;
  }

  @Test
  @Tag("slow")
  void is_ZeroHoldingsSafe() {

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());
    valuation.value(positions);
    assertThat(positions.getPositions()).isEmpty();

  }

  @Test
  @Tag("slow")
  void is_AssetAndCurrencyHydratedFromValuationRequest() {

    Asset asset = AssetUtils.getAsset("EBAY", "NASDAQ");

    Positions positions = getValuedPositions(asset, "2019-10-18");
    Position position = positions.get(asset);
    assertThat(position)
        .hasFieldOrProperty("asset");

    assertThat(position.getAsset().getMarket()).hasNoNullFieldsOrPropertiesExcept("aliases");

    assertThat(position.getMoneyValues().get(Position.In.PORTFOLIO).getCurrency())
        .hasNoNullFieldsOrProperties();
    assertThat(position.getMoneyValues().get(Position.In.BASE).getCurrency())
        .hasNoNullFieldsOrProperties();
    assertThat(position.getMoneyValues().get(Position.In.TRADE).getCurrency())
        .hasNoNullFieldsOrProperties();

  }


}
