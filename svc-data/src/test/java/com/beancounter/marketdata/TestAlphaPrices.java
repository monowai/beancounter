package com.beancounter.marketdata;

import static com.beancounter.marketdata.utils.AlphaMockUtils.alphaContracts;
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
    assertThat(priceMapper.readValue(
        new ClassPathResource(alphaContracts + "/alphavantage-empty-response.json").getFile(),
        PriceResponse.class)).isNull();
  }

  @Test
  void is_GlobalResponse() throws Exception {
    PriceResponse marketData = priceMapper.readValue(
        new ClassPathResource(alphaContracts + "/global-response.json").getFile(),
        PriceResponse.class);

    assertThat(marketData)
        .isNotNull()
        .hasNoNullFieldsOrPropertiesExcept("id", "requestDate");
    assertThat(marketData.getData().iterator().next().getChangePercent()).isEqualTo("0.008812");

  }

  @Test
  void is_CollectionFromResponseReturnedWithDividend() throws Exception {
    PriceResponse result = priceMapper.readValue(
        new ClassPathResource(alphaContracts + "/backfill-response.json").getFile(),
        PriceResponse.class);

    assertThat(result.getData()).isNotNull().hasSize(5);
    DateUtils dateUtils = new DateUtils();
    for (MarketData marketData : result.getData()) {
      assertThat(marketData)
          .hasFieldOrProperty("volume")
          .hasFieldOrProperty("dividend")
          .hasFieldOrProperty("split");
      if (marketData.getPriceDate().compareTo(dateUtils.getDate("2020-05-01")) == 0) {
        // Dividend
        assertThat(marketData.getDividend()).isEqualTo(new BigDecimal("0.2625"));
      }
    }
  }

  @Test
  void is_MutualFundGlobalResponse() throws Exception {
    PriceResponse marketData = priceMapper.readValue(
        new ClassPathResource(alphaContracts + "/pence-price-response.json").getFile(),
        PriceResponse.class);
    assertThat(marketData)
        .isNotNull()
        .hasNoNullFieldsOrPropertiesExcept("id", "requestDate");

  }

  @Test
  void is_ResponseWithoutMarketCodeSetToUs() throws Exception {
    MarketData marketData = validateResponse(
        new ClassPathResource("contracts/alpha/alphavantage-nasdaq.json").getFile()
    );
    assertThat(
        marketData.getAsset())
        .hasFieldOrPropertyWithValue("code", "NDAQ")
        .hasFieldOrPropertyWithValue("market.code", "US");
  }

  private MarketData validateResponse(File jsonFile) throws Exception {
    PriceResponse priceResponse = priceMapper.readValue(jsonFile, PriceResponse.class);
    MarketData marketData = priceResponse.getData().iterator().next();
    assertThat(marketData)
        .isNotNull()
        .hasFieldOrProperty("asset")
        .hasFieldOrProperty("priceDate")
        .hasFieldOrPropertyWithValue("open", new BigDecimal("119.3700"))
        .hasFieldOrPropertyWithValue("high", new BigDecimal("121.6100"))
        .hasFieldOrPropertyWithValue("low", new BigDecimal("119.2700"))
        .hasFieldOrPropertyWithValue("close", new BigDecimal("121.3000"))
        .hasFieldOrPropertyWithValue("volume", new BigDecimal("958346").intValue())
    ;
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
