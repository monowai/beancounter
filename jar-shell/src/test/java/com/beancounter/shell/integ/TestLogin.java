package com.beancounter.shell.integ;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.shell.auth.LoginService;
import com.beancounter.shell.auth.OAuth2Response;
import com.beancounter.shell.config.AuthConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.HashMap;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {
    AuthConfig.class})
@ActiveProfiles("auth")
public class TestLogin {

  private static WireMockRule mockInternet;
  @Autowired
  private LoginService loginService;
  @Autowired
  private LoginService.AuthGateway authGateway;

  @Value("${auth.client}")
  private String client;

  private ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  @SneakyThrows
  void mockKeyCloak() {
    if (mockInternet == null) {
      mockInternet = new WireMockRule(options().port(9999));
      mockInternet.start();
    }

    // Certs!
    mockInternet
        .stubFor(
            get("/auth/realms/bc-test/protocol/openid-connect/certs")
                .willReturn(aResponse()
                    .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(mapper.writeValueAsString(
                        mapper.readValue(new ClassPathResource("./kc-certs.json")
                            .getFile(), HashMap.class))
                    )
                    .withStatus(200)));

    // Mock expired token response
    mockInternet
        .stubFor(
            post("/auth/realms/bc-test/protocol/openid-connect/token")
                .willReturn(aResponse()
                    .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(mapper.writeValueAsString(
                        mapper.readValue(new ClassPathResource("./kc-response.json")
                            .getFile(), HashMap.class))
                    )

                    .withStatus(200)));

  }

  @Test
  void is_ResponseSerializing() {
    LoginService.Login login = LoginService.Login.builder()
        .username("demo")
        .password("test")
        .client_id(client)
        .build();

    OAuth2Response oAuth2Response = authGateway.login(login);
    assertThat(oAuth2Response)
        .isNotNull()
        .hasNoNullFieldsOrProperties();
  }

  @Test
  void is_TokenExpiredThrowing() {
    assertThrows(JwtValidationException.class, () ->
        loginService.login("demo", "test")
    );

  }
}
