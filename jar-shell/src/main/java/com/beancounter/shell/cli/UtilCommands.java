package com.beancounter.shell.cli;

import com.beancounter.shell.auth.LoginService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.FileSystems;
import java.util.Map;
import java.util.TreeMap;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class UtilCommands {
  private final LoginService loginService;
  @Value("${api.path:../secrets/google-api/}")
  private String apiPath;

  @Value("${marketdata.url:http://localhost:9510/api}")
  private String marketDataUrl;

  UtilCommands(LoginService loginService) {
    this.loginService = loginService;
  }

  @ShellMethod("Current working directory")
  public String pwd() {
    return FileSystems.getDefault().getPath(".").toAbsolutePath().toString();
  }

  @ShellMethod("Secrets")
  public String api() {
    return apiPath;
  }

  @ShellMethod("Shell configuration")
  @SneakyThrows
  public String config() {
    Map<String, String> config = new TreeMap<>();
    config.put("AUTH_REALM", loginService.getRealm());
    config.put("AUTH_CLIENT", loginService.getClient());
    config.put("AUTH_URI", loginService.getUri());
    config.put("API_PATH", apiPath);
    config.put("MARKETDATA_URL", marketDataUrl);

    return new ObjectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(config);
  }

}
