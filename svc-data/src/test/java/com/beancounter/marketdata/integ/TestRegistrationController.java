package com.beancounter.marketdata.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.auth.server.AuthorityRoleConverter;
import com.beancounter.common.model.SystemUser;
import com.beancounter.marketdata.utils.RegistrationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Tag("slow")
public class TestRegistrationController {
  private MockMvc mockMvc;

  @Autowired
  private WebApplicationContext context;

  @BeforeEach
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();
  }

  @Test
  void is_RegisterMeWorking() throws Exception {
    SystemUser user = new SystemUser("user", "user@testing.com");
    Jwt token = TokenUtils.getUserToken(user);
    RegistrationUtils.registerUser(mockMvc, token);

    MvcResult performed = mockMvc.perform(
        get("/me")
            .with(jwt().jwt(token).authorities(new AuthorityRoleConverter()))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is2xxSuccessful())
        .andReturn();
    assertThat(performed.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void is_MeWithNoToken() throws Exception {
    SystemUser user = new SystemUser("user", "user@testing.com");
    Jwt token = TokenUtils.getUserToken(user);
    RegistrationUtils.registerUser(mockMvc, token);

    MvcResult performed = mockMvc.perform(
        get("/me")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError())
        .andReturn();

    assertThat(performed.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void is_MeUnregistered() throws Exception {
    SystemUser user = new SystemUser(
        "is_MeUnregistered",
        "is_MeUnregistered@testing.com");
    Jwt token = TokenUtils.getUserToken(user);

    MvcResult performed = mockMvc.perform(
        get("/me")
            .with(jwt().jwt(token).authorities(new AuthorityRoleConverter()))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError())
        .andReturn();

    assertThat(performed.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
  }

}
