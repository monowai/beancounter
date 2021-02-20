package com.beancounter.shell.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.auth.client.AuthClientConfig;
import com.beancounter.auth.common.TokenService;
import com.beancounter.auth.common.TokenUtils;
import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.client.services.PortfolioServiceClient.PortfolioGw;
import com.beancounter.client.services.RegistrationService;
import com.beancounter.common.contracts.PortfolioResponse;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.BcJson;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.shell.cli.PortfolioCommands;
import com.beancounter.shell.config.ShellConfig;
import java.util.Collections;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

  private static final TokenService tokenService = new TokenService();
  private static PortfolioCommands portfolioCommands;
  private static PortfolioGw portfolioGw;
  @MockBean
  private RegistrationService registrationService;
  @MockBean
  private JwtDecoder jwtDecoder;
  private final BcJson bcJson = new BcJson();

  @BeforeAll
  static void mockPortfolioGw() {
    portfolioGw = Mockito.mock(PortfolioGw.class);
    PortfolioServiceClient portfolioServiceClient =
        new PortfolioServiceClient(portfolioGw, tokenService);
    portfolioCommands = new PortfolioCommands(portfolioServiceClient);

  }

  @Test
  @SneakyThrows
  void getPortfolios() {
    getSystemUser();
    String result = portfolioCommands.get();
    assertThat(result).isNotBlank();
  }

  @Test
  @SneakyThrows
  void createPortfolio() {
    SystemUser owner = getSystemUser();

    PortfoliosResponse response = new PortfoliosResponse(
        Collections.singletonList(getPortfolio("ABC", owner)));

    Mockito.when(portfolioGw.getPortfolioByCode(
        Mockito.eq(tokenService.getBearerToken()),
        Mockito.isA(String.class)))
        .thenReturn(new PortfolioResponse(PortfolioUtils.getPortfolio("ABC")));

    Mockito.when(portfolioGw.addPortfolios(
        Mockito.eq(tokenService.getBearerToken()),
        Mockito.isA(PortfoliosRequest.class)))
        .thenReturn(response);

    String result = portfolioCommands
        .add("ABC", "ABC", "NZD", "USD");
    assertThat(result).isNotNull();
    Portfolio portfolio = bcJson.getObjectMapper().readValue(result, Portfolio.class);
    assertThat(portfolio).isEqualToIgnoringGivenFields(response.getData().iterator().next(),
        "owner");
  }

  @Test
  @SneakyThrows
  void is_AddPortfolioThatExists() {

    SystemUser owner = getSystemUser();
    Portfolio existing = getPortfolio("ZZZ", owner);
    PortfolioResponse portfolioResponse = new PortfolioResponse(existing);
    Mockito.when(portfolioGw.getPortfolioByCode(tokenService.getBearerToken(), existing.getCode()))
        .thenReturn(portfolioResponse); // Portfolio exists

    String result = portfolioCommands
        .add("ZZZ", "ABC", "NZD", "USD");
    assertThat(result).isNotNull();
    Portfolio portfolio = bcJson.getObjectMapper().readValue(result, Portfolio.class);
    assertThat(portfolio).isEqualToIgnoringGivenFields(portfolioResponse.getData(),
        "owner");
  }

  private SystemUser getSystemUser() {
    SystemUser owner = new SystemUser(KeyGenUtils.format(UUID.randomUUID()));
    Jwt jwt = TokenUtils.getUserToken(owner);
    Mockito.when(jwtDecoder.decode("token")).thenReturn(jwt);
    Mockito.when(registrationService.me()).thenReturn(owner);

    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtDecoder.decode("token")));
    return owner;
  }

  private Portfolio getPortfolio(String code, SystemUser owner) {
    Portfolio toReturn = PortfolioUtils.getPortfolio(code);
    toReturn.setOwner(owner);
    return toReturn;
  }

}
