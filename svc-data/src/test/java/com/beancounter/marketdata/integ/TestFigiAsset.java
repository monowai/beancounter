package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.auth.server.AuthorityRoleConverter;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.model.SystemUser;
import com.beancounter.marketdata.utils.FigiMockUtils;
import com.beancounter.marketdata.utils.RegistrationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("figi")
@Tag("slow")
public class TestFigiAsset {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AuthorityRoleConverter authorityRoleConverter = new AuthorityRoleConverter();
  @Autowired
  private WebApplicationContext context;
  private static WireMockRule figiApi;
  private MockMvc mockMvc;
  private Jwt token;

  @Autowired
  void mockServices() throws Exception {
    if (figiApi == null) {
      figiApi = new WireMockRule(options().port(6655));
      figiApi.start();

      FigiMockUtils.mock(figiApi,
          new ClassPathResource("/contracts" + "/figi/brkb-response.json").getFile(),
          "US",
          "BRK/B",
          "Common Stock");

      this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
          .apply(springSecurity())
          .build();

      // Setup a user account
      SystemUser user = SystemUser.builder()
          .id("user")
          .email("user@testing.com")
          .build();
      token = TokenUtils.getUserToken(user);
      RegistrationUtils.registerUser(mockMvc, token);
    }
  }

  @Test
  void is_BrkBFound() throws Exception {

    MvcResult mvcResult = mockMvc.perform(
        get("/assets/{market}/{code}", "NYSE", "BRK.B")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetResponse assetResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);

    assertThat(assetResponse.getData())
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "BERKSHIRE HATHAWAY INC-CL B");

  }
}
