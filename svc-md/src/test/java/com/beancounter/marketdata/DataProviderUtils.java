package com.beancounter.marketdata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.TimeZone;
import org.springframework.http.MediaType;

/**
 * Convenience functions to reduce the LOC in a unit test.
 *
 * @author mikeh
 * @since 2019-03-09
 */
public class DataProviderUtils {

  private static ObjectMapper mapper = new ObjectMapper();

  /**
   * Convenience function to stub a response for ABC symbol.
   *
   * @param mockAlphaVantage wiremock
   * @param jsonFile         response file to return
   * @throws IOException anything
   */
  public static void mockAlphaResponse(WireMockRule mockAlphaVantage, File jsonFile)
      throws IOException {
    mockAlphaVantage
        .stubFor(
            get(urlEqualTo("/query?function=TIME_SERIES_DAILY&symbol=ABC.AX&apikey=demo"))
                .willReturn(aResponse()
                    .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(mapper.writeValueAsString(mapper.readValue(jsonFile, HashMap.class)))
                    .withStatus(200)));
  }

  public static void mockWtdResponse(
      WireMockRule wtdMock, Collection<Asset> assets, String asAt, File jsonFile)
      throws IOException {
    mockWtdResponse(assets, wtdMock, asAt, true, jsonFile);
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
      Collection<Asset> assets, WireMockRule wtdMock, String asAt,
      boolean overrideAsAt, File jsonFile)  throws IOException {

    StringBuilder assetArg = null;

    for (Asset asset : assets) {
      if (assetArg != null) {
        assetArg.append("%2C").append(asset.getCode());
      } else {
        assetArg = new StringBuilder(asset.getCode());
      }
    }

    HashMap<String, Object> response = getResponseMap(jsonFile);

    if (asAt != null && overrideAsAt) {
      response.put("date", asAt);
    }
    wtdMock
        .stubFor(
            get(urlEqualTo(
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
   * @param jsonFile input file
   * @return Map
   * @throws IOException err
   */
  public static HashMap<String, Object> getResponseMap(File jsonFile) throws IOException {
    MapType mapType = mapper.getTypeFactory()
        .constructMapType(LinkedHashMap.class, String.class, Object.class);

    return mapper.readValue(jsonFile, mapType);
  }

  /**
   * Nasdaq.
   *
   * @return Proper representation of Nasdaq
   */
  public static Market nasdaq() {
    return Market.builder()
        .code("NASDAQ")
        .timezone(TimeZone.getTimeZone("US/Eastern"))
        .build();
  }


}
