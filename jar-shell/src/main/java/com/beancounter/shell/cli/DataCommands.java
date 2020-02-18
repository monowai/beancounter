package com.beancounter.shell.cli;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.shell.service.AssetService;
import com.beancounter.shell.service.PortfolioService;
import com.beancounter.shell.service.StaticService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@Slf4j
public class DataCommands {

  private AssetService assetService;
  private PortfolioService portfolioService;
  private StaticService staticService;
  private ObjectMapper objectMapper = new ObjectMapper();
  private ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();

  DataCommands(AssetService assetService,
               PortfolioService portfolioService,
               StaticService staticService) {
    this.assetService = assetService;
    this.portfolioService = portfolioService;
    this.staticService = staticService;
  }

  @ShellMethod("Supported markets")
  public String markets(
      @ShellOption(help = "Optional market code", defaultValue = "__NULL__")
          String marketCode

  ) throws JsonProcessingException {
    if (marketCode != null) {
      MarketResponse marketResponse = MarketResponse.builder().build();
      Market market = staticService.resolveMarket(
          marketCode, assetService, assetService.staticService
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
  public String portfolio(
      @ShellOption(help = "By Code")
          String portfolioCode) throws JsonProcessingException {
    Portfolio portfolio = portfolioService.getPortfolioByCode(portfolioCode);
    return writer.writeValueAsString(portfolio);
  }

  @ShellMethod("Find portfolio by id")
  public String portfolioId(
      @ShellOption(help = "By Code")
          String portfolioId) throws JsonProcessingException {
    Portfolio portfolio = portfolioService.getPortfolioById(portfolioId);
    return writer.writeValueAsString(portfolio);
  }

}
