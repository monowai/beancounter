package com.beancounter.auth;

import static com.beancounter.common.utils.BcJson.getObjectMapper;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.auth.client.AuthClientConfig;
import com.beancounter.auth.client.LoginService;
import com.beancounter.auth.client.OAuth2Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {LoginService.class, LoginService.AuthGateway.class},
    properties = {"auth.enabled=true"})
@ImportAutoConfiguration({AuthClientConfig.class})
@ActiveProfiles("auth")
public class TestClientLogin {

  private static WireMockRule mockInternet;

  @Autowired
  private LoginService loginService;
  @Autowired
  private LoginService.AuthGateway authGateway;

  @Value("${auth.client}")
  private String client;

  @BeforeEach
  void mockKeyCloak() throws Exception {
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
                    .withBody(getObjectMapper().writeValueAsString(
                        getObjectMapper().readValue(new ClassPathResource("./kc-certs.json")
                            .getFile(), HashMap.class))
                    )
                    .withStatus(200)));

    // Mock expired token response
    mockInternet
        .stubFor(
            post("/auth/realms/bc-test/protocol/openid-connect/token")
                .willReturn(aResponse()
                    .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(getObjectMapper().writeValueAsString(
                        getObjectMapper().readValue(new ClassPathResource("./kc-response.json")
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

    OAuth2Response authResponse = authGateway.login(login);
    assertThat(authResponse)
        .isNotNull()
        .hasNoNullFieldsOrProperties();
  }

  @Test
  void  is_TokenExpiredThrowing() {
    assertThrows(JwtValidationException.class, () ->
        loginService.login("demo", "test", "test")
    );

  }
}
