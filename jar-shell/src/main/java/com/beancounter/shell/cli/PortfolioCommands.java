package com.beancounter.shell.cli;

import com.beancounter.client.PortfolioService;
import com.beancounter.client.StaticService;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
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
public class PortfolioCommands {
  private PortfolioService portfolioService;
  private StaticService staticService;
  private ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

  PortfolioCommands(PortfolioService portfolioService,
                    StaticService staticService) {
    this.portfolioService = portfolioService;
    this.staticService = staticService;
  }

  @ShellMethod("Find portfolio by code")
  @SneakyThrows
  public String code(
      @ShellOption(help = "Code - case insensitive") String portfolioCode) {
    Portfolio portfolio = portfolioService.getPortfolioByCode(portfolioCode);
    return writer.writeValueAsString(portfolio);
  }

  @ShellMethod("Find by id")
  @SneakyThrows
  public String id(
      @ShellOption(help = "Primary key - case sensitive") String portfolioId) {
    Portfolio portfolio = portfolioService.getPortfolioById(portfolioId);
    return writer.writeValueAsString(portfolio);
  }

  @ShellMethod(key = "add", value = "Add portfolio")
  @SneakyThrows
  public String add(
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

    Currency base = staticService.getCurrency(baseCurrency);
    Currency ref = staticService.getCurrency(currencyCode);

    PortfoliosRequest portfoliosRequest = PortfoliosRequest.builder()
        .data(Collections.singleton(
            Portfolio.builder()
                .base(base)
                .currency(ref)
                .code(code)
                .name(name)
                .build()
        ))
        .build();
    PortfoliosResponse result = portfolioService.add(portfoliosRequest);
    return writer.writeValueAsString(result.getData().iterator().next());
  }
}
