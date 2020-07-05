package com.beancounter.common;

import static com.beancounter.common.utils.AssetUtils.getAssetInput;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.BcJson;
import com.beancounter.common.utils.DateUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class TestMarketData {
  private final DateUtils dateUtils = new DateUtils();

  static void compare(MarketData marketData, MarketData mdResponse) {
    assertThat(mdResponse)
        .isEqualToIgnoringGivenFields(marketData, "asset");
    assertThat(mdResponse.getAsset().getMarket())
        .isEqualToIgnoringGivenFields(marketData.getAsset().getMarket());
    assertThat(mdResponse.getAsset())
        .isEqualToIgnoringGivenFields(marketData.getAsset(), "market");
  }

  @Test
  void is_MarketDataSerializing() throws Exception {

    Collection<MarketData> marketDataCollection = new ArrayList<>();
    MarketData marketData = new MarketData(
        null,
        AssetUtils.getJsonAsset("Market", "Asset"),
        "TEST",
        dateUtils.getDate("2012-10-01"),
        BigDecimal.ONE, //Open
        BigDecimal.TEN, // Close
        BigDecimal.ONE,// Low
        BigDecimal.TEN, //High
        new BigDecimal("9.56"), // Previous CLOSE
        new BigDecimal("1.56"), // Change
        new BigDecimal("0.04"), // change %
        10,
        null,
        null);

    marketDataCollection.add(marketData);

    PriceResponse priceResponse = new PriceResponse(marketDataCollection);

    PriceResponse fromJson = BcJson.getObjectMapper().readValue(
        BcJson.getObjectMapper().writeValueAsString(priceResponse),
        PriceResponse.class);

    assertThat(fromJson.getData()).isNotNull();

    MarketData mdResponse = fromJson.getData().iterator().next();

    compare(marketData, mdResponse);

    assertThat(fromJson.getData().iterator().next().getChangePercent()).isEqualTo("0.04");
  }

  @Test
  void is_QuantitiesWorking() throws Exception {
    QuantityValues quantityValues = new QuantityValues();
    assertThat(quantityValues)
        .hasFieldOrPropertyWithValue("sold", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("purchased", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("adjustment", BigDecimal.ZERO)
    ;

    assertThat(quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO);
    String json = BcJson.getObjectMapper().writeValueAsString(quantityValues);
    assertThat(BcJson.getObjectMapper().readValue(json, QuantityValues.class))
        .isEqualToComparingFieldByField(quantityValues);
  }

  @Test
  void is_PriceRequestSerializing() throws Exception {
    Collection<AssetInput> assets = new ArrayList<>();
    assets.add(getAssetInput("XYZ", "ABC"));
    PriceRequest priceRequest = new PriceRequest("2019-11-11", assets);
    String json = BcJson.getObjectMapper().writeValueAsString(priceRequest);
    PriceRequest fromJson = BcJson.getObjectMapper().readValue(json, PriceRequest.class);
    assertThat(fromJson.getAssets().iterator().next())
        .isEqualToIgnoringGivenFields(
            priceRequest.getAssets().iterator().next(), "market");
  }

}
