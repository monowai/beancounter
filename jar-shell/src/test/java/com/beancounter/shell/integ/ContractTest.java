package com.beancounter.shell.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.shell.cli.DataCommands;
import com.beancounter.shell.cli.PortfolioCommands;
import com.beancounter.shell.cli.UtilCommands;
import com.beancounter.shell.config.AuthConfig;
import com.beancounter.shell.config.GoogleAuthConfig;
import com.beancounter.shell.config.ShareSightConfig;
import com.beancounter.shell.config.ShellConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@ActiveProfiles("test")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@SpringBootTest(classes = {
    ShellConfig.class,
    AuthConfig.class,
    GoogleAuthConfig.class,
    ShareSightConfig.class})
public class ContractTest {
  @Autowired
  private DataCommands dataCommands;

  @Autowired
  private UtilCommands utilCommands;

  @Autowired
  private PortfolioCommands portfolioCommands;

  @Autowired
  private PromptProvider promptProvider;

  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void is_MarketCommandsReturning() throws Exception {
    String json = dataCommands.markets("MOCK");
    MarketResponse marketResponse = objectMapper.readValue(json, MarketResponse.class);
    assertThat(marketResponse.getData()).isNotNull().hasSize(1);

    json = dataCommands.markets(null);
    marketResponse = objectMapper.readValue(json, MarketResponse.class);
    assertThat(marketResponse.getData()).isNotNull().hasSizeGreaterThan(3);
    assertThrows(BusinessException.class, () -> dataCommands.markets("Illegal"));
  }

  @Test
  void is_PortfolioByCode() throws Exception {
    String json = portfolioCommands.code("TEST");
    Portfolio portfolio = objectMapper.readValue(json, Portfolio.class);
    assertThat(portfolio).isNotNull();

    assertThrows(BusinessException.class, () -> portfolioCommands.code("ILLEGAL"));
  }

  @Test
  void is_PortfolioById() throws Exception {
    String json = portfolioCommands.id("TEST");
    Portfolio portfolio = objectMapper.readValue(json, Portfolio.class);
    assertThat(portfolio).isNotNull();

    assertThrows(BusinessException.class, () -> portfolioCommands.code("ILLEGAL"));
  }

  @Test
  void is_UtilCommands() {
    assertThat(utilCommands.api()).isNotNull().isNotBlank();
    assertThat(utilCommands.pwd()).isNotNull().isNotBlank();
  }

  @Test
  @SneakyThrows
  void is_ConfigReturned() {
    String config = utilCommands.config();
    assertThat(config).isNotNull();
    TypeReference<HashMap<String, String>> typeRef = new TypeReference<>() {
    };
    Map<String, String> configMap = new ObjectMapper().readValue(config, typeRef);
    assertThat(configMap).isNotEmpty();
  }

  @Test
  void is_PromptAvailable() {
    assertThat(promptProvider).isNotNull();
    assertThat(promptProvider.getPrompt()).isNotNull();
  }
}
