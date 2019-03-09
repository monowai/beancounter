package com.beancounter.marketdata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.springframework.http.MediaType;

/**
 * Convenience functions to reduce the LOC in a unit test.
 *
 * @author mikeh
 * @since 2019-03-09
 */
public class AlphaVantageUtils {

  private static ObjectMapper mapper = new ObjectMapper();

  /**
   * Convenience function to stub a response for ABC symbol.
   *
   * @param mockAlphaVantage wiremock
   * @param jsonFile response file to return
   * @throws IOException anything
   */
  public static void mockResponse(WireMockRule mockAlphaVantage, File jsonFile) throws IOException {
    mockAlphaVantage
        .stubFor(
            get(urlEqualTo("/query?function=TIME_SERIES_DAILY&symbol=ABC&apikey=123"))
                .willReturn(aResponse()
                    .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(mapper.writeValueAsString(mapper.readValue(jsonFile, HashMap.class)))
                    .withStatus(200)));
  }
}
