package com.beancounter.auth.client;

import static org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.PASSWORD;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
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
@EnableFeignClients(basePackages = "com.beancounter.auth")
public class LoginService {
  private final AuthGateway authGateway;

  private final JwtDecoder jwtDecoder;

  @Value("${spring.security.oauth2.registration.custom.client-id:bc-service}")
  private String clientId;

  @Value("${spring.security.oauth2.registration.custom.client-secret:not-set}")
  private String secret;

  public LoginService(AuthGateway authGateway, JwtDecoder jwtDecoder) {
    this.authGateway = authGateway;
    this.jwtDecoder = jwtDecoder;
  }

  public void login(String user, String password, String client) {
    Login login = Login.builder()
        .username(user)
        .password(password)
        .client_id(client)
        .build();
    OAuth2Response response = authGateway.login(login);
    assert response != null;
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtDecoder.decode(response.getToken())));
    log.info("Logged in as {}", user);
  }

  /**
   * m2m login using preconfigured secret.
   *
   * <p>Sets the authentication token if the call is successful.
   *
   * @return token
   */
  public String login() {
    if ("not-set".equals(secret)) {
      return null;
    }

    Login login = Login.builder()
        .grant_type(CLIENT_CREDENTIALS.getValue())
        .client_id(clientId)
        .client_secret(secret)
        .build();
    OAuth2Response response = authGateway.login(login);
    assert response != null;
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtDecoder.decode(response.getToken())));
    log.info("Service logged into {}", clientId);
    return response.getToken();
  }

  @FeignClient(name = "oauth",
      url = "${auth.uri:http://keycloak:9620/auth}", configuration = AuthBeans.class)
  public interface AuthGateway {
    @PostMapping(value = "/realms/${auth.realm:bc-dev}/protocol/openid-connect/token",
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
    private String client_secret;
    @Builder.Default
    private String grant_type = PASSWORD.getValue();
  }

}