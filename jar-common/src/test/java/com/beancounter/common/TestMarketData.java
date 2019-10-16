package com.beancounter.common;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.DateUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TestMarketData {
  @Test
  @VisibleForTesting
  void is_MarketDataSerializing() throws Exception {
    DateUtils dateUtils = new DateUtils();
    ObjectMapper objectMapper = new ObjectMapper();
    MarketData marketData = MarketData.builder()
        .asset(getAsset("Asset", "Market"))
        .close(BigDecimal.TEN)
        .open(BigDecimal.ONE)
        .close(BigDecimal.TEN)
        .high(BigDecimal.TEN)
        .date(dateUtils.getDate("2012-10-01", "yyyy-MM-dd"))
        .build();

    MarketData fromJson = objectMapper.readValue(
        objectMapper.writeValueAsString(marketData),
        MarketData.class);
    assertThat(fromJson).isEqualToComparingFieldByField(marketData);
  }
}
