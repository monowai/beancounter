package com.beancounter.shell.auth;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

@Service
@Configuration
@Slf4j
public class LoginService {
  private AuthGateway authGateway;
  @Value("${auth.realm}")
  private String realm;
  @Value("${auth.client}")
  private String client;
  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
  private String certPath;

  private JwtDecoder jwtDecoder;

  LoginService(AuthGateway authGateway, JwtDecoder jwtDecoder) {
    this.authGateway = authGateway;
    this.jwtDecoder = jwtDecoder;
  }

  public void login(String user, String password) {
    Login login = Login.builder()
        .username(user)
        .password(password)
        .client_id(client)
        .build();
    OAuth2Response response = authGateway.login(login);
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtDecoder.decode(response.getToken())));
    log.info("Logged in as {}", user);
  }

  @FeignClient(name = "oauth",
      url = "${auth.uri:http://keycloak:9620}", configuration = AuthBeans.class)
  public interface AuthGateway {
    @PostMapping(value = "/auth/realms/${auth.realm:bc-dev}/protocol/openid-connect/token",
        consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
        produces = {MediaType.APPLICATION_JSON_VALUE})
    OAuth2Response login(Login login);


  }

  @Data
  @Builder
  @SuppressWarnings("checkstyle:MemberName")
  public static class Login {
    private String username;
    private String password;
    private String client_id;
    @Builder.Default
    private String grant_type = "password";
  }

}
