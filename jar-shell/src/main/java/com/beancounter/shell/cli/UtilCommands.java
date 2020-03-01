package com.beancounter.shell.cli;

import com.beancounter.shell.auth.LoginService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class UtilCommands {
  private final LoginService loginService;
  @Value("${api.path:../secrets/google-api/}")
  private String apiPath;

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
    Map<String, String> config = new HashMap<>();
    config.put("auth.realm", loginService.getRealm());
    config.put("auth.client", loginService.getClient());
    config.put("auth.uri", loginService.getUri());
    config.put("api.path", apiPath);
    config.put("working.dir", pwd());

    return new ObjectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(config);
  }

}
