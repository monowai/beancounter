package com.beancounter.position.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.helper.AssetHelper;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.config.TransactionConfiguration;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import com.beancounter.position.service.Accumulator;
import com.beancounter.position.service.Valuation;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Market Valuation testing.
 *
 * @author mikeh
 * @since 2019-02-25
 */
@ExtendWith(SpringExtension.class)
@Tag("slow")
@ImportAutoConfiguration({FeignAutoConfiguration.class})
@SpringBootTest
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.CLASSPATH,
    ids = "beancounter:svc-md:+:stubs:8090")
@DirtiesContext
@ActiveProfiles("test")

class TestMarketValues {

  @Autowired
  private Valuation valuation;

  @Test
  @Tag("slow")
  @VisibleForTesting
  void is_MarketValuationCalculated() {
    Asset asset = AssetHelper.getAsset("ABC", "marketCode");

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    // We need to have a Quantity in order to get the price, so create a position
    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(asset)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Accumulator accumulator = new Accumulator(new TransactionConfiguration());
    Position position = accumulator.accumulate(buy, Position.builder().asset(asset).build());
    positions.add(position);

    positions = valuation.value(positions);

    MoneyValues localMoney = positions.get(asset).getMoneyValue(Position.In.LOCAL);
    assertThat(localMoney)
        .hasFieldOrPropertyWithValue("unrealisedGain", new BigDecimal("8000.00"))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("100.00"))
        .hasFieldOrPropertyWithValue("marketValue", new BigDecimal("10000.00"))
        .hasFieldOrPropertyWithValue("totalGain", new BigDecimal("8000.00"))
    ;
  }

  @Test
  @Tag("slow")
  @VisibleForTesting
  void is_AssetHydratedFromValuationRequest() throws Exception {

    Asset asset = AssetHelper.getAsset("EBAY", "NASDAQ");


    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    // We need to have a Quantity in order to get the price, so create a position

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(asset)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Accumulator accumulator = new Accumulator(new TransactionConfiguration());
    Position position = accumulator.accumulate(buy, Position.builder().asset(asset).build());
    positions.add(position);

    positions = valuation.value(positions);

    Position position1 = positions.get(asset);
    assertThat(position1)
        .hasFieldOrProperty("asset");

    assertThat(position1.getAsset().getMarket()).hasNoNullFieldsOrPropertiesExcept("aliases");

  }


}
