package com.beancounter.shell.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.auth.TokenHelper;
import com.beancounter.auth.TokenService;
import com.beancounter.client.PortfolioService.PortfolioGw;
import com.beancounter.client.RegistrationService;
import com.beancounter.client.StaticService;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.shell.cli.DataCommands;
import com.beancounter.shell.config.AuthConfig;
import com.beancounter.shell.config.ShellConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
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
    ShellConfig.class, AuthConfig.class
})
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@ActiveProfiles("test")
public class TestDataCommands {
  @Autowired
  private DataCommands dataCommands;
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

    Jwt jwt = TokenHelper.getUserToken(SystemUser.builder().build());
    Mockito.when(jwtDecoder.decode("token")).thenReturn(jwt);
    SystemUser owner = SystemUser.builder().build();
    Mockito.when(registrationService.me()).thenReturn(owner);

    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtDecoder.decode("token")));

    PortfolioRequest portfolioRequest = getPortfolioRequest(owner);

    PortfolioRequest response = getPortfolioResponse(owner);
    Mockito.when(portfolioGw.addPortfolios(
        Mockito.eq(tokenService.getBearerToken()),
        Mockito.isA(PortfolioRequest.class)))
        .thenReturn(response);

    String result = dataCommands
        .addPortfolio("ABC", "ABC", "NZD", "USD");
    assertThat(result).isNotNull();
    Portfolio portfolio = new ObjectMapper().readValue(result, Portfolio.class);
    assertThat(portfolio).isEqualTo(response.getData().iterator().next());
  }

  private PortfolioRequest getPortfolioRequest(SystemUser owner) {

    Portfolio toCreate = PortfolioUtils.getPortfolio("ABC");
    toCreate.setId(null);
    toCreate.setName(toCreate.getCode());
    toCreate.setOwner(owner);
    toCreate.setCurrency(staticService.getCurrency("NZD"));
    toCreate.setBase(staticService.getCurrency("USD"));

    PortfolioRequest portfolioRequest = PortfolioRequest.builder().build();
    portfolioRequest.setData(Collections.singletonList(toCreate));
    return portfolioRequest;
  }

  private PortfolioRequest getPortfolioResponse(SystemUser owner) {

    Portfolio toReturn = PortfolioUtils.getPortfolio("ABC");
    toReturn.setId("createdId");
    toReturn.setName(toReturn.getCode());
    toReturn.setOwner(owner);
    toReturn.setCurrency(staticService.getCurrency("NZD"));
    toReturn.setBase(staticService.getCurrency("USD"));

    PortfolioRequest portfolioRequest = PortfolioRequest.builder().build();
    portfolioRequest.setData(Collections.singletonList(toReturn));
    return portfolioRequest;
  }

}
