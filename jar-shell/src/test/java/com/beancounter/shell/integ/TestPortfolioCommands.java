package com.beancounter.shell.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.auth.client.AuthClientConfig;
import com.beancounter.auth.common.TokenService;
import com.beancounter.auth.common.TokenUtils;
import com.beancounter.client.services.PortfolioServiceClient.PortfolioGw;
import com.beancounter.client.services.RegistrationService;
import com.beancounter.client.services.StaticService;
import com.beancounter.common.contracts.PortfolioResponse;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.shell.cli.PortfolioCommands;
import com.beancounter.shell.config.ShellConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {
    ShellConfig.class, AuthClientConfig.class
})
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@ActiveProfiles("test")
public class TestPortfolioCommands {
  @Autowired
  private PortfolioCommands portfolioCommands;
  @Autowired
  private StaticService staticService;
  @Autowired
  private TokenService tokenService;

  @MockBean
  private RegistrationService registrationService;

  @MockBean
  private PortfolioGw portfolioGw;

  @MockBean
  private JwtDecoder jwtDecoder;

  @Test
  @SneakyThrows
  void createPortfolio() {

    SystemUser owner = getSystemUser();

    PortfoliosResponse response = PortfoliosResponse.builder()
        .data(Collections.singletonList(getPortfolio("ABC", owner)))
        .build();
    Mockito.when(portfolioGw.addPortfolios(
        Mockito.eq(tokenService.getBearerToken()),
        Mockito.isA(PortfoliosRequest.class)))
        .thenReturn(response);

    String result = portfolioCommands
        .add("ABC", "ABC", "NZD", "USD");
    assertThat(result).isNotNull();
    Portfolio portfolio = new ObjectMapper().readValue(result, Portfolio.class);
    assertThat(portfolio).isEqualTo(response.getData().iterator().next());
  }

  @Test
  @SneakyThrows
  void is_AddPortfolioThatExists() {

    SystemUser owner = getSystemUser();
    Portfolio existing = getPortfolio("ZZZ", owner);
    PortfolioResponse response = PortfolioResponse.builder().data(existing).build();
    Mockito.when(portfolioGw.getPortfolioByCode(tokenService.getBearerToken(), existing.getCode()))
        .thenReturn(response); // Portfolio exists

    String result = portfolioCommands
        .add("ZZZ", "ABC", "NZD", "USD");
    assertThat(result).isNotNull();
    Portfolio portfolio = new ObjectMapper().readValue(result, Portfolio.class);
    assertThat(portfolio).isEqualTo(response.getData());
  }

  private SystemUser getSystemUser() {
    Jwt jwt = TokenUtils.getUserToken(SystemUser.builder()
        .id(KeyGenUtils.format(UUID.randomUUID()))
        .build());
    Mockito.when(jwtDecoder.decode("token")).thenReturn(jwt);
    SystemUser owner = SystemUser.builder().build();
    Mockito.when(registrationService.me()).thenReturn(owner);

    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtDecoder.decode("token")));
    return owner;
  }

  private Portfolio getPortfolio(String code, SystemUser owner) {
    Portfolio toReturn = PortfolioUtils.getPortfolio(code);
    toReturn.setId("createdId");
    toReturn.setOwner(owner);
    toReturn.setCurrency(staticService.getCurrency("NZD"));
    toReturn.setBase(staticService.getCurrency("USD"));
    return toReturn;
  }

}
