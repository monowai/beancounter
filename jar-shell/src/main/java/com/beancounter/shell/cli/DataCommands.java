package com.beancounter.shell.cli;

import com.beancounter.shell.service.AssetService;
import com.beancounter.shell.service.BcService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
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
      return writer.writeValueAsString(assetService.resolveMarket(marketCode));
    } else {
      return writer.writeValueAsString(bcService.getMarkets());
    }

  }

}
