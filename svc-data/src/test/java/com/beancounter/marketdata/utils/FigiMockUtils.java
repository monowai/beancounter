package com.beancounter.marketdata.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.marketdata.assets.figi.FigiResponse;
import com.beancounter.marketdata.assets.figi.FigiSearch;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

public class FigiMockUtils {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @SneakyThrows
  public static void mock(WireMockRule figiApi,
                          File jsonFile,
                          String market,
                          String code,
                          String securityType) {

    FigiSearch search = FigiSearch.builder()
        .idValue(code)
        .securityType2(securityType)
        .exchCode(market)
        .build();
    Collection<FigiSearch> searchCollection = new ArrayList<>();
    searchCollection.add(search);

    Collection<FigiResponse> response = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
        jsonFile, new TypeReference<>() {});

    figiApi
        .stubFor(
            post(
                urlEqualTo("/v2/mapping"))
                .withRequestBody(equalToJson(objectMapper.writeValueAsString(searchCollection)))
                .withHeader("X-OPENFIGI-APIKEY", matching("demoxx"))
                .willReturn(aResponse()
                    .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper
                        .writeValueAsString(response))
                    .withStatus(200)));

  }
}
