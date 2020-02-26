package com.beancounter.shell;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.shell.auth.LoginService;
import com.beancounter.shell.config.AuthConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.HashMap;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {
    AuthConfig.class})
@ActiveProfiles("auth")
public class TestLogin {

  @Autowired
  private LoginService loginService;

  private static WireMockRule mockInternet;

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
    // ToDo: Figure out how to return a token that can be decoded
    //    Jwt jwt = TokenHelper.getUserToken(SystemUser.builder().build());
    //    OAuth2Response oAuth2Response = new OAuth2Response();
    //    oAuth2Response.setExpiry(jwt.getExpiresAt().toEpochMilli());
    //    oAuth2Response.setToken(jwt.getTokenValue());

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
  void is_LoginAuthenticationSetting() {
    // Static expired token; for the time being this is as far as I'm taking it.
    assertThrows(JwtValidationException.class, () ->
        loginService.login("demo", "test")
    );

  }
}
