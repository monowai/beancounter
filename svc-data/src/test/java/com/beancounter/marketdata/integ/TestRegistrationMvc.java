package com.beancounter.marketdata.integ;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.AuthorityRoleConverter;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.model.SystemUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

public class TestRegistrationMvc {

  private static AuthorityRoleConverter authorityRoleConverter = new AuthorityRoleConverter();

  @SneakyThrows
  public static void registerUser(MockMvc mockMvc, Jwt token, SystemUser user) {

    mockMvc.perform(
        post("/register")
            .with(
                jwt(token)
                    .authorities(authorityRoleConverter))
            .with(csrf())
            .content(new ObjectMapper()
                .writeValueAsBytes(RegistrationRequest.builder()
                    .email(user.getEmail()).build()
                ))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();
  }

}
