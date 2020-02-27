package com.beancounter.shell.cli;

import com.beancounter.auth.TokenService;
import com.beancounter.client.RegistrationService;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.SystemUser;
import com.beancounter.shell.auth.LoginService;
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

  UserCommands(LoginService loginService,
               RegistrationService registrationService,
               TokenService tokenService) {
    this.loginService = loginService;
    this.registrationService = registrationService;
    this.tokenService = tokenService;
  }

  @Autowired
  public void setLineReader(@Lazy LineReader lineReader) {
    this.lineReader = lineReader;
  }

  @ShellMethod("Identify yourself")
  public void login(
      @ShellOption(help = "User ID") String user) {

    String password = lineReader.readLine("Password: ", '*');
    this.loginService.login(user, password);

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
      throw new BusinessException("Please login");
    }
    Object email = token.getTokenAttributes().get("email");
    if (email == null) {
      throw new BusinessException("No email address");
    }
    RegistrationRequest registrationRequest = RegistrationRequest.builder()
        .email(email.toString())
        .build();
    return objectMapper.writeValueAsString(this.registrationService.register(registrationRequest));


  }

}
