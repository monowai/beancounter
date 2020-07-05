package com.beancounter.shell.cli;

import com.beancounter.client.services.StaticService;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.model.Market;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.ArrayList;
import java.util.Collection;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@Slf4j
public class DataCommands {

  private final StaticService staticService;
  private final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

  DataCommands(StaticService staticService) {
    this.staticService = staticService;
  }

  @ShellMethod("Supported markets")
  @SneakyThrows
  public String markets(
      @ShellOption(help = "Optional market code", defaultValue = "__NULL__")
          String marketCode) {
    if (marketCode != null) {
      Market market = staticService.getMarket(
          marketCode
      );
      if (market != null) {
        Collection<Market> markets = new ArrayList<>();
        markets.add(market);
        return writer.writeValueAsString(new MarketResponse(markets));
      }
      return "Not Found";
    } else {
      return writer.writeValueAsString(staticService.getMarkets());
    }

  }


}
