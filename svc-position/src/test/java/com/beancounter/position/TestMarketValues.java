package com.beancounter.position;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.helper.AssetHelper;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.config.TransactionConfiguration;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import com.beancounter.position.service.Accumulator;
import com.beancounter.position.service.Valuation;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Market Valuation testing.
 *
 * @author mikeh
 * @since 2019-02-25
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class TestMarketValues {

  private static WireMockRule mockMarketData;
  @Autowired
  private Valuation valuation;

  @Autowired
  private void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (mockMarketData == null) {
      mockMarketData = new WireMockRule(options().port(9999));
      mockMarketData.start();
    }
  }

  @Test
  @Tag("slow")
  @VisibleForTesting
  void is_MarketValuationCalculated() throws Exception {
    Asset asset = AssetHelper.getAsset("ABC", "marketCode");
    Collection<Asset> assets = new ArrayList<>();
    assets.add(asset);

    CollectionType javaType = TestUtils.getMapper().getTypeFactory()
        .constructCollectionType(Collection.class, MarketData.class);

    File jsonFile = new ClassPathResource("contracts/price-simple.json").getFile();
    Object response = TestUtils.getMapper().readValue(jsonFile, javaType);

    TestUtils.mockMarketData(mockMarketData, TestUtils.getMapper().writeValueAsString(assets),
        TestUtils.getMapper().writeValueAsString(response));

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

    CollectionType javaType = TestUtils.getMapper().getTypeFactory()
        .constructCollectionType(Collection.class, MarketData.class);

    Asset asset = AssetHelper.getAsset("EBAY", "NASDAQ");
    Collection<Asset> assets = new ArrayList<>();
    assets.add(asset);

    File jsonFile = new ClassPathResource("contracts/price-response.json").getFile();
    Object response = TestUtils.getMapper().readValue(jsonFile, javaType);

    TestUtils.mockMarketData(mockMarketData, TestUtils.getMapper().writeValueAsString(assets),
        TestUtils.getMapper().writeValueAsString(response));

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
