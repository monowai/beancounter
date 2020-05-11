package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.AssetSearchResponse;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.marketdata.providers.alpha.AlphaPriceAdapter;
import com.beancounter.marketdata.utils.AlphaMockUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Market Data integration with AlphaVantage.co
 *
 * @author mikeh
 * @since 2019-03-03
 */
class TestAlphaAssets {
  private final ObjectMapper assetMapper = new AlphaPriceAdapter().getAlphaMapper();

  @Test
  void is_NullAsset() throws Exception {
    File jsonFile = new ClassPathResource(AlphaMockUtils.alphaContracts
        + "/alphavantage-empty-response.json").getFile();

    assertThat(assetMapper.readValue(jsonFile, PriceResponse.class)).isNull();
  }

  @Test
  void is_SearchResult() throws Exception {
    File jsonFile = new ClassPathResource(AlphaMockUtils.alphaContracts
        + "/mf-search.json").getFile();
    AssetSearchResponse searchResponse = assetMapper.readValue(jsonFile, AssetSearchResponse.class);
    assertThat(searchResponse)
        .isNotNull()
        .hasFieldOrProperty("data");

    assertThat(searchResponse.getData())
        .hasSize(1);
    assertThat(searchResponse.getData().iterator().next())
        .hasFieldOrPropertyWithValue("name", "AXA Framlington Health Fund Z GBP Acc")
        .hasFieldOrPropertyWithValue("type", "Mutual Fund")
        .hasFieldOrPropertyWithValue("symbol", "0P0000XMSV.LON");
  }

}
