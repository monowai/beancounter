package com.beancounter.shell.cli;

import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.BcJson;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class PortfolioCommands {
  private final Logger log = LoggerFactory.getLogger(PortfolioCommands.class);
  private final PortfolioServiceClient portfolioService;
  private final BcJson bcJson = new BcJson();

  public PortfolioCommands(PortfolioServiceClient portfolioService) {
    this.portfolioService = portfolioService;
  }

  @ShellMethod("Find portfolio by code")
  public String code(
      @ShellOption(help = "Code - case insensitive") String portfolioCode)
      throws JsonProcessingException {
    Portfolio portfolio = portfolioService.getPortfolioByCode(portfolioCode);
    return bcJson.getWriter().writeValueAsString(portfolio);
  }

  @ShellMethod("My Portfolios")
  public String get()
      throws JsonProcessingException {
    PortfoliosResponse portfolio = portfolioService.getPortfolios();
    if (portfolio.getData().isEmpty()) {
      return "No portfolios";
    }
    return bcJson.getWriter().writeValueAsString(portfolio.getData());
  }

  @ShellMethod("Find by id")
  public String id(
      @ShellOption(help = "Primary key - case sensitive") String portfolioId)
      throws JsonProcessingException {
    Portfolio portfolio = portfolioService.getPortfolioById(portfolioId);
    return bcJson.getWriter().writeValueAsString(portfolio);
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
      return bcJson.getWriter().writeValueAsString(portfolio);
    } catch (BusinessException e) {
      log.info("Creating portfolio {}", code);
    }

    PortfoliosRequest portfoliosRequest = new PortfoliosRequest(
        Collections.singleton(
            new PortfolioInput(code, name, currencyCode, baseCurrency))
    );
    PortfoliosResponse result = portfolioService.add(portfoliosRequest);
    return bcJson.getWriter().writeValueAsString(result.getData().iterator().next());
  }
}
