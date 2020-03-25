package com.beancounter.shell.cli;

import com.beancounter.auth.client.LoginService;
import com.beancounter.auth.client.TokenService;
import com.beancounter.client.services.RegistrationService;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.exception.UnauthorizedException;
import com.beancounter.shell.config.EnvConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  private LoginService loginService;
  private RegistrationService registrationService;
  private TokenService tokenService;
  private LineReader lineReader;
  private ObjectMapper objectMapper = new ObjectMapper();
  private EnvConfig envConfig;

  UserCommands(LoginService loginService,
               RegistrationService registrationService,
               TokenService tokenService,
               EnvConfig envConfig) {
    this.loginService = loginService;
    this.registrationService = registrationService;
    this.tokenService = tokenService;
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
    return tokenService.getToken();
  }

  @ShellMethod("Who am I?")
  @SneakyThrows
  public String me() {
    return objectMapper.writeValueAsString(this.registrationService.me());
  }

  @ShellMethod("Register your Account")
  @SneakyThrows
  public String register() {
    JwtAuthenticationToken token = tokenService.getJwtToken();
    if (token == null) {
      throw new UnauthorizedException("Please login");
    }
    return objectMapper
        .writeValueAsString(
            this.registrationService.register(RegistrationRequest.builder().build())
        );


  }

}
