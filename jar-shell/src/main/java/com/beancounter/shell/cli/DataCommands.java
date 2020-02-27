package com.beancounter.shell.cli;

import com.beancounter.client.PortfolioService;
import com.beancounter.client.RegistrationService;
import com.beancounter.client.StaticService;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.Collections;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@Slf4j
public class DataCommands {

  private PortfolioService portfolioService;
  private StaticService staticService;
  private RegistrationService registrationService;
  private ObjectMapper objectMapper = new ObjectMapper();
  private ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();

  DataCommands(PortfolioService portfolioService,
               StaticService staticService,
               RegistrationService registrationService) {
    this.portfolioService = portfolioService;
    this.staticService = staticService;
    this.registrationService = registrationService;
  }

  @ShellMethod("Supported markets")
  @SneakyThrows
  public String markets(
      @ShellOption(help = "Optional market code", defaultValue = "__NULL__")
          String marketCode) {
    if (marketCode != null) {
      MarketResponse marketResponse = MarketResponse.builder().build();
      Market market = staticService.resolveMarket(
          marketCode
      );

      if (market != null) {
        marketResponse.getData().add(market);
      }
      return writer.writeValueAsString(marketResponse);
    } else {
      return writer.writeValueAsString(staticService.getMarkets());
    }

  }

  @ShellMethod("Find portfolio by code")
  @SneakyThrows
  public String portfolio(
      @ShellOption(help = "By Code") String portfolioCode) {
    Portfolio portfolio = portfolioService.getPortfolioByCode(portfolioCode);
    return writer.writeValueAsString(portfolio);
  }

  @ShellMethod("Find portfolio by id")
  @SneakyThrows
  public String portfolioId(
      @ShellOption(help = "By Code") String portfolioId) {
    Portfolio portfolio = portfolioService.getPortfolioById(portfolioId);
    return writer.writeValueAsString(portfolio);
  }

  @ShellMethod("Add portfolio")
  @SneakyThrows
  public String addPortfolio(
      @ShellOption(help = "Unique Code") String code,
      @ShellOption(help = "Name") String name,
      @ShellOption(help = "Reference currency") String currencyCode,
      @ShellOption(help = "Base currency") String baseCurrency
  ) {
    Portfolio portfolio;
    try {
      portfolio = portfolioService.getPortfolioByCode(code);
      return writer.writeValueAsString(portfolio);
    } catch (BusinessException e) {
      log.info("Creating portfolio {}", code);
    }

    SystemUser systemUser = registrationService.me();
    Currency base = staticService.getCurrency(baseCurrency);
    Currency ref = staticService.getCurrency(currencyCode);

    PortfolioRequest portfolioRequest = PortfolioRequest.builder()
        .data(Collections.singleton(
            Portfolio.builder()
                .base(base)
                .currency(ref)
                .code(code)
                .name(name)
                .owner(systemUser)
                .build()
        ))
        .build();
    PortfolioRequest result = portfolioService.add(portfolioRequest);
    assert result != null;
    return writer.writeValueAsString(result.getData().iterator().next());
  }

}
