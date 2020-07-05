package com.beancounter.shell.cli;

import com.beancounter.auth.client.LoginService;
import com.beancounter.client.services.RegistrationService;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.exception.UnauthorizedException;
import com.beancounter.common.utils.BcJson;
import com.beancounter.shell.config.EnvConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.SneakyThrows;
import org.jline.reader.LineReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class UserCommands {

  private final LoginService loginService;
  private final RegistrationService registrationService;
  private final EnvConfig envConfig;
  private LineReader lineReader;

  public UserCommands(LoginService loginService,
                      RegistrationService registrationService,
                      EnvConfig envConfig) {
    this.loginService = loginService;
    this.registrationService = registrationService;
    this.envConfig = envConfig;
  }

  @Autowired
  public void setLineReader(@Lazy LineReader lineReader) {
    this.lineReader = lineReader;
  }

  @ShellMethod("Identify yourself")
  public void login(
      @ShellOption(help = "User ID") String user) {

    String password = lineReader.readLine("Password: ", '*');
    this.loginService.login(user, password, envConfig.getClient());

  }

  @ShellMethod("What's my access token?")
  public String token() {
    return registrationService.getToken();
  }

  @ShellMethod("Who am I?")
  @SneakyThrows
  public String me() {
    return BcJson.getWriter().writeValueAsString(this.registrationService.me());
  }

  @ShellMethod("Register your Account")
  public String register() throws JsonProcessingException {
    JwtAuthenticationToken token = registrationService.getJwtToken();
    if (token == null) {
      throw new UnauthorizedException("Please login");
    }
    return BcJson.getWriter()
        .writeValueAsString(
            this.registrationService
                .register(new RegistrationRequest(token.getToken().getClaim("email")))
        );


  }

}
