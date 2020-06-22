package com.beancounter.position.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.input.TrustedTrnQuery;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.position.PositionBoot;
import com.beancounter.position.service.Valuation;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = PositionBoot.class,
    properties = {"auth.enabled=false"},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext
@AutoConfigureMessageVerifier
@AutoConfigureWireMock(port = 0)
@WebAppConfiguration
public class ContractVerifierBase {
  private final ObjectMapper om = new ObjectMapper();
  private final DateUtils dateUtils = new DateUtils();
  @MockBean
  JwtDecoder jwtDecoder;
  @Autowired
  private WebApplicationContext context;
  @MockBean
  private Valuation valuationService;
  @MockBean
  private PortfolioServiceClient portfolioServiceClient;

  @BeforeEach
  @SneakyThrows
  public void initMocks() {
    MockMvc mockMvc = MockMvcBuilders
        .webAppContextSetup(context)
        .build();

    RestAssuredMockMvc.mockMvc(mockMvc);

    Portfolio testPortfolio = Portfolio.builder()
        .code("TEST")
        .id("TEST")
        .name("NZD Portfolio")
        .currency(Currency.builder().code("NZD").name("Dollar").symbol("$").build())
        .base(Currency.builder().code("USD").name("Dollar").symbol("$").build())
        .build();
    Mockito.when(portfolioServiceClient.getPortfolioByCode("TEST"))
        .thenReturn(testPortfolio);

    Mockito.when(portfolioServiceClient.getPortfolioById("TEST"))
        .thenReturn(testPortfolio);

    Mockito.when(valuationService.build(TrustedTrnQuery.builder()
        .assetId("KMI")
        .tradeDate(dateUtils.getDate("2020-05-01"))
        .portfolio(testPortfolio)
        .build()))
        .thenReturn(om.readValue(
            new ClassPathResource("contracts/kmi-response.json").getFile(),
            PositionResponse.class));

    Mockito.when(valuationService.build(TrustedTrnQuery.builder()
        .assetId("MSFT")
        .tradeDate(dateUtils.getDate("2020-05-01"))
        .portfolio(testPortfolio)
        .build()))
        .thenReturn(om.readValue(
            new ClassPathResource("contracts/msft-response.json").getFile(),
            PositionResponse.class));

    Mockito.when(valuationService.build(testPortfolio, "2020-05-01"))
        .thenReturn(om.readValue(
            new ClassPathResource("contracts/test-response.json").getFile(),
            PositionResponse.class));
  }

  @Test
  void is_Started() {
    assertThat(valuationService).isNotNull();
  }
}
