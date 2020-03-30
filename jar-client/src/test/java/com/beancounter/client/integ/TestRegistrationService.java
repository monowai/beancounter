package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.client.config.ClientConfig;
import com.beancounter.client.services.RegistrationService;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.exception.UnauthorizedException;
import com.beancounter.common.model.SystemUser;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@ImportAutoConfiguration(ClientConfig.class)
@SpringBootTest(classes = ClientConfig.class)
public class TestRegistrationService {
  @Autowired
  private RegistrationService registrationService;

  @MockBean
  private JwtDecoder jwtDecoder;

  @Test
  void is_RegisteringAuthenticatedUser() {
    setupAuth("token");
    // Currently matching is on email
    SystemUser registeredUser = registrationService
        .register(RegistrationRequest.builder().email("blah@blah.com").build());
    assertThat(registeredUser).hasNoNullFieldsOrProperties();
  }

  @Test
  void is_UnauthenticatedUserRejectedFromRegistration() {
    // Setup the authenticated context
    setupAuth("not@authenticated.com");

    assertThrows(UnauthorizedException.class, () -> registrationService
        .register(RegistrationRequest.builder().build()));
  }

  private void setupAuth(String email) {
    Jwt jwt = TokenUtils.getUserToken(SystemUser.builder()
        .id(email)
        .email(email)
        .build());
    Mockito.when(jwtDecoder.decode(email)).thenReturn(jwt);
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtDecoder.decode(email)));
  }

}
