package com.beancounter.shell.cli;

import com.beancounter.shell.config.EnvConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.FileSystems;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class UtilCommands {
  private final EnvConfig envConfig;

  public UtilCommands(EnvConfig envConfig) {
    this.envConfig = envConfig;
  }

  @ShellMethod("Current working directory")
  public String pwd() {
    return FileSystems.getDefault().getPath("")
        .toAbsolutePath().toString();
  }

  @ShellMethod("Secrets")
  public String api() {
    return envConfig.getApiPath();
  }

  @ShellMethod("Shell configuration")
  public String config() throws JsonProcessingException {
    Map<String, String> config = new TreeMap<>();
    config.put("AUTH_REALM", envConfig.getRealm());
    config.put("AUTH_CLIENT", envConfig.getClient());
    config.put("AUTH_URI", envConfig.getUri());
    config.put("API_PATH", envConfig.getApiPath());
    config.put("MARKETDATA_URL", envConfig.getMarketDataUrl());

    return new ObjectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(config);
  }

}
