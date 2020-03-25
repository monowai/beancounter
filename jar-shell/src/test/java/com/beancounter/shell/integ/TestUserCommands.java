package com.beancounter.shell.integ;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.auth.TokenUtils;
import com.beancounter.auth.client.LoginService;
import com.beancounter.auth.client.OAuth2Response;
import com.beancounter.auth.client.TokenService;
import com.beancounter.client.services.RegistrationService;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.contracts.RegistrationResponse;
import com.beancounter.common.exception.UnauthorizedException;
import com.beancounter.common.model.SystemUser;
import com.beancounter.shell.cli.UserCommands;
import com.beancounter.shell.config.ShellConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.jline.reader.LineReader;
import org.jline.reader.impl.LineReaderImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest(classes = {
    UserCommands.class,
    ShellConfig.class,
    RegistrationService.class,
    TokenService.class,
    RegistrationService.RegistrationGateway.class,
    LoginService.class,
    LoginService.AuthGateway.class,
    LineReaderImpl.class

})
public class TestUserCommands {
  @Autowired
  private UserCommands userCommands;

  @Autowired
  private TokenService tokenService;

  @MockBean
  private LineReader lineReader;

  @MockBean
  private LoginService.AuthGateway authGateway;

  @MockBean
  private RegistrationService.RegistrationGateway registrationGateway;

  @MockBean
  private JwtDecoder jwtDecoder;

  @Value("${auth.client}")
  private String client;

  @Test
  void is_UnauthorizedThrowing() {
    SecurityContextHolder.getContext().setAuthentication(null);
    Mockito.when(registrationGateway.me(tokenService.getBearerToken()))
        .thenReturn(null);
    assertThrows(UnauthorizedException.class, () -> userCommands.me());

    Mockito.when(registrationGateway
        .register(tokenService.getBearerToken(), RegistrationRequest.builder().build()))
        .thenReturn(null);
    assertThrows(UnauthorizedException.class, () -> userCommands.register());
  }

  @Test
  @SneakyThrows
  void is_LoginReturningMe() {
    String user = "simple";
    String password = "password";

    Mockito.when(lineReader.readLine("Password: ", '*')).thenReturn(password);

    LoginService.Login login = LoginService.Login.builder()
        .username(user)
        .password(password)
        .client_id(client)
        .build();

    Jwt jwt = TokenUtils.getUserToken(
        SystemUser.builder()
            .id(user)
            .email("someone@nowhere.com")
            .build());

    OAuth2Response authResponse = new OAuth2Response();
    authResponse.setToken(user);
    Mockito.when(authGateway.login(login)).thenReturn(authResponse);
    Mockito.when(jwtDecoder.decode(authResponse.getToken())).thenReturn(jwt);
    // Can I login?
    userCommands.login(user);

    // Is my token in the SecurityContext and am I Me?
    Mockito.when(registrationGateway.me(tokenService.getBearerToken()))
        .thenReturn(RegistrationResponse.builder().data(SystemUser.builder()
            .id(user)
            .email("someone@nowhere.com")
            .build()).build());
    SystemUser me = new ObjectMapper().readValue(userCommands.me(), SystemUser.class);
    assertThat(me).isNotNull();

    assertThat(userCommands.token()).isEqualTo(authResponse.getToken());

    Mockito.when(registrationGateway.register(tokenService.getBearerToken(),
        RegistrationRequest.builder().build()))
        .thenReturn(RegistrationResponse.builder().data(SystemUser.builder()
            .id(user)
            .build()).build());

    SystemUser registered = new ObjectMapper().readValue(userCommands.register(),
        SystemUser.class);

    assertThat(registered).isNotNull().hasFieldOrPropertyWithValue("id", user);
  }
}
