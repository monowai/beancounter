package com.beancounter.marketdata.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.marketdata.assets.figi.FigiSearch;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.util.HashMap;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

public class FigiMockUtils {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @SneakyThrows
  public static void mock(WireMockRule figiApi, File jsonFile, String market, String code) {
    FigiSearch search = FigiSearch.builder()
        .query(code)
        .exchCode(market)
        .build();

    figiApi
        .stubFor(
            post(
                urlEqualTo("/v2/search"))
                .withRequestBody(equalToJson(objectMapper.writeValueAsString(search)))
                .withHeader("X-OPENFIGI-APIKEY", matching("demo"))
                .willReturn(aResponse()
                    .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper
                        .writeValueAsString(objectMapper.readValue(jsonFile, HashMap.class)))
                    .withStatus(200)));

  }
}
