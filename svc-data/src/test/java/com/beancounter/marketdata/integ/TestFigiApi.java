package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.auth.server.AuthorityRoleConverter;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.SystemUser;
import com.beancounter.marketdata.assets.figi.FigiProxy;
import com.beancounter.marketdata.markets.MarketService;
import com.beancounter.marketdata.utils.FigiMockUtils;
import com.beancounter.marketdata.utils.RegistrationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("figi")
@Tag("slow")
public class TestFigiApi {

  private static WireMockRule figiApi;

  @Autowired
  private FigiProxy figiProxy;

  @Autowired
  private MarketService marketService;

  @Autowired
  private WebApplicationContext context;

  @Autowired
  @SneakyThrows
  void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (figiApi == null) {
      figiApi = new WireMockRule(options().port(6666));
      figiApi.start();
      String prefix = "/contracts/figi";
      FigiMockUtils.mock(figiApi,
          new ClassPathResource(prefix + "/common-stock-response.json").getFile(),
          "US",
          "MSFT",
          "Common Stock");

      FigiMockUtils.mock(figiApi,
          new ClassPathResource(prefix + "/adr-response.json").getFile(),
          "US",
          "BAIDU",
          "Depositary Receipt");

      FigiMockUtils.mock(figiApi,
          new ClassPathResource(prefix + "/reit-response.json").getFile(),
          "US",
          "OHI",
          "REIT");

      FigiMockUtils.mock(figiApi,
          new ClassPathResource(prefix + "/mf-response.json").getFile(),
          "US",
          "XLF",
          "REIT");

      FigiMockUtils.mock(figiApi,
          new ClassPathResource(prefix + "/brkb-response.json").getFile(),
          "US",
          "BRK/B",
          "Common Stock");

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
    Asset asset = figiProxy.find(marketService.getMarket("NASDAQ"), "MSFT");
    assertThat(asset)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "MICROSOFT CORP")
        .isNotNull();
  }

  @Test
  void is_AdrFound() {
    Asset asset = figiProxy.find(marketService.getMarket("NASDAQ"), "BAIDU");
    assertThat(asset)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "BAIDU INC - SPON ADR")
        .isNotNull();
  }

  @Test
  void is_ReitFound() {
    Asset asset = figiProxy.find(marketService.getMarket("NYSE"), "OHI");
    assertThat(asset)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "OMEGA HEALTHCARE INVESTORS")
        .isNotNull();
  }

  @Test
  void is_MutualFundFound() {
    Asset asset = figiProxy.find(marketService.getMarket("NYSE"), "XLF");
    assertThat(asset)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "FINANCIAL SELECT SECTOR SPDR")
        // Unknown to BC, but is known to FIGI
        .hasNoNullFieldsOrPropertiesExcept("id", "priceSymbol")
        .isNotNull();
  }

  @Test
  void is_BrkBFound() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();

    // Authorise the caller to access the BC API
    SystemUser user = SystemUser.builder()
        .id("user")
        .email("user@testing.com")
        .build();
    Jwt token = TokenUtils.getUserToken(user);
    RegistrationUtils.registerUser(mockMvc, token);

    MvcResult mvcResult = mockMvc.perform(
        get("/assets/{market}/{code}", "NYSE", "BRK.B")
            .with(jwt().jwt(token).authorities(new AuthorityRoleConverter()))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetResponse assetResponse = new ObjectMapper()
        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);

    assertThat(assetResponse.getData())
        .isNotNull()
        .hasFieldOrPropertyWithValue("code", "BRK.B")
        .hasFieldOrPropertyWithValue("name", "BERKSHIRE HATHAWAY INC-CL B");

  }

}
