package com.beancounter.shell.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.shell.cli.DataCommands;
import com.beancounter.shell.cli.ShellPrompt;
import com.beancounter.shell.cli.UtilCommands;
import com.beancounter.shell.config.GoogleAuthConfig;
import com.beancounter.shell.config.ShareSightConfig;
import com.beancounter.shell.service.PortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    ShareSightConfig.class,
    DataCommands.class,
    PortfolioService.class,
    ShellPrompt.class,
    GoogleAuthConfig.class,
    UtilCommands.class})
public class CommandTest {
  @Autowired
  private DataCommands dataCommands;

  @Autowired
  private UtilCommands utilCommands;

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
    String json = dataCommands.portfolio("TEST");
    Portfolio portfolio = objectMapper.readValue(json, Portfolio.class);
    assertThat(portfolio).isNotNull();

    assertThrows(BusinessException.class, () -> dataCommands.portfolio("ILLEGAL"));
  }

  @Test
  void is_PortfolioById() throws Exception {
    String json = dataCommands.portfolioId("TEST");
    Portfolio portfolio = objectMapper.readValue(json, Portfolio.class);
    assertThat(portfolio).isNotNull();

    assertThrows(BusinessException.class, () -> dataCommands.portfolio("ILLEGAL"));
  }

  @Test
  void is_UtilCommands() {
    assertThat(utilCommands.api()).isNotNull().isNotBlank();
    assertThat(utilCommands.pwd()).isNotNull().isNotBlank();
  }

  @Test
  void is_PromptAvailable() {
    assertThat(promptProvider).isNotNull();
    assertThat(promptProvider.getPrompt()).isNotNull();
  }
}
