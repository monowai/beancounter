package com.beancounter.marketdata.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.providers.wtd.WtdResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;

/**
 * WorldTradingData mocking support.
 */
public class WtdMockUtils {
  public static final String WTD_PATH = "/contracts/wtd";
  private static final ObjectMapper mapper = new ObjectMapper();

  private static DateUtils dateUtils = new DateUtils();
  private static LocalDate zonedDateTime = LocalDate.now(dateUtils.defaultZone);

  public static final String priceDate =
      dateUtils.getLastMarketDate(zonedDateTime, ZoneId.systemDefault()).toString();

  public static WtdResponse get(String date, Map<String, MarketData> prices) {
    WtdResponse response = new WtdResponse();
    response.setDate(date);
    response.setData(prices);
    return response;
  }

  public static MarketData get(String date, Asset asset, String open,
                               String close, String high, String low, String volume) {
    return MarketData.builder()
        .open(new BigDecimal(open))
        .close(new BigDecimal(close))
        .high(new BigDecimal(high))
        .low(new BigDecimal(low))
        .volume(Integer.decode(volume))
        .priceDate(dateUtils.getDate(date))
        .asset(asset).build();
  }

  public static WtdResponse get(String message) {
    WtdResponse wtdResponse = new WtdResponse();
    wtdResponse.setMessage(message);
    return wtdResponse;
  }

  /**
   * Mock the WTD response.
   *
   * @param wtdMock      Bind to this
   * @param assets       Assets for which an argument will be created from
   * @param asAt         Date that will be requested in the response
   * @param overrideAsAt if true, then asAt will override the mocked priceDate
   * @param jsonFile     Read response from this file.
   * @throws IOException error
   */
  public static void mockWtdResponse(
      Collection<AssetInput> assets, WireMockRule wtdMock, String asAt,
      boolean overrideAsAt, File jsonFile) throws IOException {

    StringBuilder assetArg = null;

    for (AssetInput input : assets) {
      Market market = input.getResolvedAsset().getMarket();
      Asset asset = input.getResolvedAsset();
      String suffix = "";
      if (!(market.getCode().equalsIgnoreCase("NASDAQ"))) {
        // Horrible hack to support WTD contract mocking ASX/AX
        suffix = "." + (market.getCode().equalsIgnoreCase("ASX")
            ? "AX" : market.getCode());
      }

      if (assetArg != null) {
        assetArg.append("%2C").append(asset.getCode()).append(suffix);
      } else {
        assetArg = new StringBuilder(asset.getCode()).append(suffix);
      }
    }

    HashMap<String, Object> response = getResponseMap(jsonFile);

    if (asAt != null && overrideAsAt) {
      response.put("date", asAt);
    }
    wtdMock
        .stubFor(
            WireMock.get(urlEqualTo(
                "/api/v1/history_multi_single_day?symbol=" + assetArg
                    + "&date=" + asAt
                    + "&api_token=demo"))
                .willReturn(aResponse()
                    .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(mapper.writeValueAsString(response))
                    .withStatus(200)));
  }

  /**
   * Helper to return a Map from a JSON file.
   *
   * @param jsonFile input file
   * @return Map
   * @throws IOException err
   */
  public static HashMap<String, Object> getResponseMap(File jsonFile) throws IOException {
    MapType mapType = mapper.getTypeFactory()
        .constructMapType(LinkedHashMap.class, String.class, Object.class);

    return mapper.readValue(jsonFile, mapType);
  }
}
