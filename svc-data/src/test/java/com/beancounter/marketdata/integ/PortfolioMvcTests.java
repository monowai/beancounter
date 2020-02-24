package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.integ.TestRegistrationMvc.registerUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.AuthorityRoleConverter;
import com.beancounter.auth.TokenHelper;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.CurrencyUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Tag("slow")
@SpringBootTest
@WebAppConfiguration
class PortfolioMvcTests {

  private ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private AuthorityRoleConverter authorityRoleConverter;

  @BeforeEach
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();
  }

  @Test
  void is_findingByIdCode() throws Exception {
    SystemUser user = SystemUser.builder()
        .id("portfolioB")
        .email("portfolioB@testing.com")
        .build();

    Portfolio portfolio = Portfolio.builder()
        .code("Twix")
        .name("NZD Portfolio")
        .currency(CurrencyUtils.getCurrency("NZD"))
        .owner(user)
        .build();

    Jwt token = TokenHelper.getUserToken(user);
    registerUser(mockMvc, token, user);

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            // Mocking does not use the JwtRoleConverter configured in ResourceServerConfig
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper()
                .writeValueAsBytes(PortfolioRequest.builder()
                    .data(Collections.singleton(portfolio))
                    .build()))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfolioRequest portfolioRequest = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfolioRequest.class);

    assertThat(portfolioRequest)
        .isNotNull()
        .hasFieldOrProperty("data");

    assertThat(portfolioRequest.getData())
        .hasSize(1);

    String code = portfolioRequest.getData().iterator().next().getCode();
    String id = portfolioRequest.getData().iterator().next().getId();

    mockMvc.perform(
        get("/portfolios/{code}/code", "abc")
            .with(jwt(token).authorities(authorityRoleConverter))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError())
        .andReturn();

    mockMvc.perform(
        get("/portfolios/{code}/code", code)
            .with(jwt(token).authorities(authorityRoleConverter))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    mockMvc.perform(
        get("/portfolios/{id}", id)
            .with(jwt(token).authorities(authorityRoleConverter))
            .with(csrf())

            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    mockMvc.perform(
        get("/portfolios/{id}", "invalidId")
            .with(jwt(token).authorities(authorityRoleConverter))
            .with(csrf())

            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError())
        .andReturn();

    MvcResult mvcResult = mockMvc.perform(
        get("/portfolios")
            .with(jwt(token).authorities(authorityRoleConverter))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfolioRequest found = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfolioRequest.class);

    assertThat(found.getData()).isNotEmpty();

  }

  @Test
  void is_persistAndFindPortfoliosWorking() throws Exception {
    SystemUser user = SystemUser.builder()
        .id("portfolioA")
        .email("portfolioA@testing.com")
        .build();


    Portfolio portfolio = Portfolio.builder()
        .code("TWEE")
        .name("NZD Portfolio")
        .currency(CurrencyUtils.getCurrency("NZD"))
        .owner(user)
        .build();

    Jwt token = TokenHelper.getUserToken(user);
    registerUser(mockMvc, token, user);

    Collection<Portfolio> portfolios = new ArrayList<>();
    portfolios.add(portfolio);
    PortfolioRequest createRequest = PortfolioRequest.builder()
        .data(portfolios)
        .build();

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(createRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfolioRequest portfolioRequest = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfolioRequest.class);

    assertThat(portfolioRequest)
        .isNotNull()
        .hasFieldOrProperty("data");

    assertThat(portfolioRequest.getData())
        .hasSize(1);

    portfolioRequest.getData().forEach(foundPortfolio -> assertThat(foundPortfolio)
        .hasFieldOrProperty("id")
        .hasFieldOrPropertyWithValue("code", portfolio.getCode())
        .hasFieldOrPropertyWithValue("name",
            portfolio.getCurrency().getCode() + " Portfolio")
        .hasFieldOrPropertyWithValue("currency.code", "NZD")
        .hasFieldOrProperty("owner")
        .hasFieldOrProperty("base"));

  }

}

