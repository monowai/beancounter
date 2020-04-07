package com.beancounter.shell.cli;

import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.model.Portfolio;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@Slf4j
public class PortfolioCommands {
  private PortfolioServiceClient portfolioService;
  private ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

  PortfolioCommands(PortfolioServiceClient portfolioService) {
    this.portfolioService = portfolioService;
  }

  @ShellMethod("Find portfolio by code")
  public String code(
      @ShellOption(help = "Code - case insensitive") String portfolioCode)
      throws JsonProcessingException {
    Portfolio portfolio = portfolioService.getPortfolioByCode(portfolioCode);
    return writer.writeValueAsString(portfolio);
  }

  @ShellMethod("My Portfolios")
  public String get()
      throws JsonProcessingException {
    PortfoliosResponse portfolio = portfolioService.getPortfolios();
    if (portfolio == null || portfolio.getData() == null) {
      return "No portfolios";
    }
    return writer.writeValueAsString(portfolio.getData());
  }

  @ShellMethod("Find by id")
  public String id(
      @ShellOption(help = "Primary key - case sensitive") String portfolioId)
      throws JsonProcessingException {
    Portfolio portfolio = portfolioService.getPortfolioById(portfolioId);
    return writer.writeValueAsString(portfolio);
  }

  @ShellMethod(key = "add", value = "Add portfolio")
  public String add(
      @ShellOption(help = "Unique Code") String code,
      @ShellOption(help = "Name") String name,
      @ShellOption(help = "Reference currency") String currencyCode,
      @ShellOption(help = "Base currency") String baseCurrency
  ) throws JsonProcessingException {
    Portfolio portfolio;
    try {
      portfolio = portfolioService.getPortfolioByCode(code);
      return writer.writeValueAsString(portfolio);
    } catch (BusinessException e) {
      log.info("Creating portfolio {}", code);
    }

    PortfoliosRequest portfoliosRequest = PortfoliosRequest.builder()
        .data(Collections.singleton(
            PortfolioInput.builder()
                .base(baseCurrency)
                .currency(currencyCode)
                .code(code)
                .name(name)
                .build()
        ))
        .build();
    PortfoliosResponse result = portfolioService.add(portfoliosRequest);
    return writer.writeValueAsString(result.getData().iterator().next());
  }
}
