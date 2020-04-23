package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.common.model.Asset;
import com.beancounter.marketdata.assets.figi.FigiProxy;
import com.beancounter.marketdata.utils.FigiMockUtils;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
public class TestFigiApi {

  private static WireMockRule figiApi;

  @Autowired
  private FigiProxy figiProxy;

  @Autowired
  @SneakyThrows
  void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (figiApi == null) {
      figiApi = new WireMockRule(options().port(6666));
      figiApi.start();
      String prefix = "/contracts";
      FigiMockUtils.mock(figiApi,
          new ClassPathResource(prefix + "/figi/common-stock-response.json").getFile(),
          "US",
          "MSFT",
          "Common Stock");

      FigiMockUtils.mock(figiApi,
          new ClassPathResource(prefix + "/figi/adr-response.json").getFile(),
          "US",
          "BAIDU",
          "Depositary Receipt");

      FigiMockUtils.mock(figiApi,
          new ClassPathResource(prefix + "/figi/reit-response.json").getFile(),
          "US",
          "OHI",
          "REIT");

      FigiMockUtils.mock(figiApi,
          new ClassPathResource(prefix + "/figi/mf-response.json").getFile(),
          "US",
          "XLF",
          "REIT");

      figiApi.stubFor(any(anyUrl())
          .atPriority(10)
          .willReturn(aResponse()
              .withStatus(200)
              .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .withBody("[{\"error\": \"No identifier found.\"\n"
                  + "    }\n"
                  + "]")));
    }
  }

  @Test
  void is_CommonStockFound() {
    Asset asset = figiProxy.find("NASDAQ", "MSFT");
    assertThat(asset)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "MICROSOFT CORP")
        .isNotNull();
  }

  @Test
  void is_AdrFound() {
    Asset asset = figiProxy.find("NASDAQ", "BAIDU");
    assertThat(asset)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "BAIDU INC - SPON ADR")
        .isNotNull();
  }

  @Test
  void is_ReitFound() {
    Asset asset = figiProxy.find("NYSE", "OHI");
    assertThat(asset)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "OMEGA HEALTHCARE INVESTORS")
        .isNotNull();
  }

  @Test
  void is_MutualFundFound() {
    Asset asset = figiProxy.find("NYSE", "XLF");
    assertThat(asset)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "FINANCIAL SELECT SECTOR SPDR")
        .hasNoNullFieldsOrPropertiesExcept("id") // Unknown to BC, but is known to FIGI
        .isNotNull();
  }

}
