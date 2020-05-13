package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.providers.alpha.AlphaConfig;
import com.beancounter.marketdata.providers.alpha.AlphaPriceAdapter;
import com.beancounter.marketdata.providers.alpha.AlphaService;
import com.beancounter.marketdata.utils.AlphaMockUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Market Data integration with AlphaVantage.co
 *
 * @author mikeh
 * @since 2019-03-03
 */
class TestAlphaPrices {
  private final ObjectMapper priceMapper = new AlphaPriceAdapter().getAlphaMapper();

  @Test
  void is_NullAsset() throws Exception {
    File jsonFile = new ClassPathResource(AlphaMockUtils.alphaContracts
        + "/alphavantage-empty-response.json").getFile();

    assertThat(priceMapper.readValue(jsonFile, PriceResponse.class)).isNull();
  }

  @Test
  void is_GlobalResponse() throws Exception {
    File jsonFile = new ClassPathResource(AlphaMockUtils.alphaContracts
        + "/global-response.json").getFile();
    PriceResponse marketData = priceMapper.readValue(jsonFile, PriceResponse.class);
    assertThat(marketData)
        .isNotNull()
        .hasNoNullFieldsOrPropertiesExcept("id", "requestDate");
    assertThat(marketData.getData().iterator().next().getChangePercent()).isEqualTo("0.008812");

  }

  @Test
  void is_MutualFundGlobalResponse() throws Exception {
    File jsonFile = new ClassPathResource(AlphaMockUtils.alphaContracts
        + "/pence-price-response.json").getFile();
    PriceResponse marketData = priceMapper.readValue(jsonFile, PriceResponse.class);
    assertThat(marketData)
        .isNotNull()
        .hasNoNullFieldsOrPropertiesExcept("id", "requestDate");

  }

  @Test
  void is_ResponseWithMarketCodeSerialized() throws Exception {
    File jsonFile = new ClassPathResource(AlphaMockUtils.alphaContracts
        + "/alphavantage-asx.json").getFile();
    MarketData marketData = validateResponse(jsonFile);
    assertThat(
        marketData.getAsset())
        .hasFieldOrPropertyWithValue("code", "MSFT")
        .hasFieldOrPropertyWithValue("market.code", "NASDAQ");

  }

  @Test
  void is_ResponseWithoutMarketCodeSetToUs() throws Exception {
    File jsonFile = new ClassPathResource(AlphaMockUtils.alphaContracts
        + "/alphavantage-nasdaq.json").getFile();
    MarketData marketData = validateResponse(jsonFile);
    assertThat(
        marketData.getAsset())
        .hasFieldOrPropertyWithValue("code", "MSFT")
        .hasFieldOrPropertyWithValue("market.code", "US");
  }

  private MarketData validateResponse(File jsonFile) throws Exception {
    PriceResponse priceResponse = priceMapper.readValue(jsonFile, PriceResponse.class);
    MarketData marketData = priceResponse.getData().iterator().next();
    assertThat(marketData)
        .isNotNull()
        .hasFieldOrProperty("asset")
        .hasFieldOrProperty("priceDate")
        .hasFieldOrPropertyWithValue("open", new BigDecimal("112.0400"))
        .hasFieldOrPropertyWithValue("high", new BigDecimal("112.8800"))
        .hasFieldOrPropertyWithValue("low", new BigDecimal("111.7300"))
        .hasFieldOrPropertyWithValue("close", new BigDecimal("112.0300"));
    return marketData;
  }


  @Test
  void is_KnownMarketVariancesHandled() {
    AlphaConfig alphaConfig = new AlphaConfig();
    AlphaService alphaService = new AlphaService(alphaConfig);
    // No configured support to handle the market
    assertThat(alphaService.isMarketSupported(Market.builder().code("NZX").build())).isFalse();

    Asset msft = AssetUtils.getAsset("NASDAQ", "MSFT");
    assertThat(alphaConfig.getPriceCode(msft)).isEqualTo("MSFT");

    Asset ohi = AssetUtils.getAsset("NYSE", "OHI");
    assertThat(alphaConfig.getPriceCode(ohi)).isEqualTo("OHI");


    Asset abc = AssetUtils.getAsset("AMEX", "ABC");
    assertThat(alphaConfig.getPriceCode(abc)).isEqualTo("ABC");

    Asset nzx = AssetUtils.getAsset("NZX", "AIRNZ");
    assertThat(alphaConfig.getPriceCode(nzx)).isEqualTo("AIRNZ.NZX");

  }

  @Test
  void is_PriceDateAccountingForWeekends() {
    DateUtils dateUtils = new DateUtils();

    AlphaConfig alphaConfig = new AlphaConfig();
    Market nasdaq = Market.builder()
        .code("NASDAQ")
        .timezone(TimeZone.getTimeZone("US/Eastern"))
        .build();
    // Sunday
    LocalDate computedDate = alphaConfig.getMarketDate(nasdaq, "2020-04-26");
    // Resolves to Friday
    assertThat(computedDate).isEqualTo(dateUtils.getDate("2020-04-24"));
  }

  @Test
  void is_PriceDateInThePastStatic() {
    DateUtils dateUtils = new DateUtils();

    AlphaConfig alphaConfig = new AlphaConfig();
    Market nasdaq = Market.builder()
        .code("NASDAQ")
        .timezone(TimeZone.getTimeZone("US/Eastern"))
        .build();
    // Sunday
    LocalDate computedDate = alphaConfig.getMarketDate(nasdaq, "2020-04-28");
    // Resolves to Friday
    assertThat(computedDate).isEqualTo(dateUtils.getDate("2020-04-28"));
  }

}
