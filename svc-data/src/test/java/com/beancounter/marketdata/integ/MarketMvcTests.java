package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.integ.TestRegistrationMvc.registerUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.AuthorityRoleConverter;
import com.beancounter.auth.JwtRoleConverter;
import com.beancounter.auth.TokenHelper;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.SystemUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
class MarketMvcTests {

  private ObjectMapper objectMapper = new ObjectMapper();
  @Autowired
  private WebApplicationContext wac;

  private MockMvc mockMvc;

  private Jwt token;

  private AuthorityRoleConverter authorityRoleConverter
      = new AuthorityRoleConverter(new JwtRoleConverter());


  @BeforeEach
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
        .apply(springSecurity())
        .build();
    SystemUser user = SystemUser.builder()
        .id("MarketMvcTests")
        .email("MarketMvcTests@testing.com")
        .build();

    token = TokenHelper.getUserToken(user);
    registerUser(mockMvc, token, user);

  }

  @Test
  void is_AllMarketsFound() throws Exception {

    MvcResult mvcResult = mockMvc.perform(
        get("/markets/")
            .with(jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    MarketResponse marketResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), MarketResponse.class);
    assertThat(marketResponse).isNotNull().hasFieldOrProperty("data");
    assertThat(marketResponse.getData()).isNotEmpty();
  }

  @Test
  void is_SingleMarketFoundCaseInsensitive() throws Exception {
    MvcResult mvcResult = mockMvc.perform(
        get("/markets/nzx")
            .with(jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    MarketResponse marketResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), MarketResponse.class);
    assertThat(marketResponse).isNotNull().hasFieldOrProperty("data");
    assertThat(marketResponse.getData()).hasSize(1);

    Market nzx = marketResponse.getData().iterator().next();
    assertThat(nzx).hasNoNullFieldsOrPropertiesExcept("currencyId", "timezoneId");
  }

  @Test
  void is_SingleMarketBadRequest() throws Exception {
    ResultActions result = mockMvc.perform(
        get("/markets/non-existent")
            .with(jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError());

    assertThat(result.andReturn().getResolvedException())
        .isNotNull()
        .isInstanceOfAny(BusinessException.class);

  }

}
