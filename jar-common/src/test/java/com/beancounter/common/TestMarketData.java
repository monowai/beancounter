package com.beancounter.common;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class TestMarketData {
  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @VisibleForTesting
  void is_MarketDataSerializing() throws Exception {
    DateUtils dateUtils = new DateUtils();


    Collection<MarketData> marketDataCollection = new ArrayList<>();
    marketDataCollection.add(MarketData.builder()
        .asset(AssetUtils.getAsset("Asset", "Market"))
        .close(BigDecimal.TEN)
        .open(BigDecimal.ONE)
        .close(BigDecimal.TEN)
        .high(BigDecimal.TEN)
        .date(dateUtils.getDate("2012-10-01", "yyyy-MM-dd"))
        .build());

    PriceResponse priceResponse = PriceResponse.builder().data(marketDataCollection).build();

    PriceResponse fromJson = objectMapper.readValue(
        objectMapper.writeValueAsString(priceResponse),
        PriceResponse.class);

    assertThat(fromJson).isEqualToComparingFieldByField(priceResponse);
  }

  @Test
  @VisibleForTesting
  void is_QuantitiesWorking() throws Exception {
    QuantityValues quantityValues = QuantityValues.builder().build();
    assertThat(quantityValues)
        .hasFieldOrPropertyWithValue("sold", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("purchased", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("adjustment", BigDecimal.ZERO)
    ;

    assertThat(quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO);
    String json = objectMapper.writeValueAsString(quantityValues);
    assertThat(objectMapper.readValue(json, QuantityValues.class))
        .isEqualToComparingFieldByField(quantityValues);
  }

}
