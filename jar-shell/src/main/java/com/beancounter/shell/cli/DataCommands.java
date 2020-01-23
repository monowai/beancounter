package com.beancounter.shell.cli;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.shell.service.AssetService;
import com.beancounter.shell.service.BcService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@Slf4j
public class DataCommands {

  private BcService bcService;
  private AssetService assetService;
  private ObjectMapper objectMapper = new ObjectMapper();
  private ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();

  @Autowired
  void setAssetService(AssetService assetService, BcService bcService) {
    this.assetService = assetService;
    this.bcService = bcService;
  }

  @ShellMethod("Supported markets")
  public String markets(
      @ShellOption(help = "Optional market code", defaultValue = "__NULL__")
          String marketCode

  ) throws JsonProcessingException {
    if (marketCode != null) {
      MarketResponse marketResponse = MarketResponse.builder().build();
      Market market = assetService.resolveMarket(marketCode);
      if (market != null) {
        marketResponse.getData().add(market);
      }
      return writer.writeValueAsString(marketResponse);
    } else {
      return writer.writeValueAsString(bcService.getMarkets());
    }

  }

  @ShellMethod("Find portfolio by code")
  public String portfolio(
      @ShellOption(help = "By Code")
          String portfolioCode) throws JsonProcessingException {
    Portfolio portfolio = bcService.getPortfolioByCode(portfolioCode);
    PortfolioRequest portfolioRequest = PortfolioRequest.builder()
        .data(Collections.singleton(portfolio))
        .build();
    return writer.writeValueAsString(portfolioRequest);
  }

  @ShellMethod("Find portfolio by id")
  public String portfolioId(
      @ShellOption(help = "By Code")
          String portfolioId) throws JsonProcessingException {
    Portfolio portfolio = bcService.getPortfolioById(portfolioId);
    PortfolioRequest portfolioRequest = PortfolioRequest.builder()
        .data(Collections.singleton(portfolio))
        .build();
    return writer.writeValueAsString(portfolioRequest);
  }

}
