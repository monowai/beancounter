package com.beancounter.shell.cli;

import com.beancounter.client.services.StaticService;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.model.Market;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@Slf4j
public class DataCommands {

  private StaticService staticService;
  private ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

  DataCommands(StaticService staticService) {
    this.staticService = staticService;
  }

  @ShellMethod("Supported markets")
  @SneakyThrows
  public String markets(
      @ShellOption(help = "Optional market code", defaultValue = "__NULL__")
          String marketCode) {
    if (marketCode != null) {
      MarketResponse marketResponse = MarketResponse.builder().build();
      Market market = staticService.getMarket(
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


}
