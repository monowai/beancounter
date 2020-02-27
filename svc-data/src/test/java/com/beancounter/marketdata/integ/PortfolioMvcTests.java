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
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.CurrencyUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import lombok.SneakyThrows;
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

  private AuthorityRoleConverter authorityRoleConverter = new AuthorityRoleConverter();

  @BeforeEach
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();
  }

  @Test
  void is_findingByIdCode() throws Exception {
    SystemUser user = SystemUser.builder()
        .build();

    Portfolio portfolio = Portfolio.builder()
        .code(UUID.randomUUID().toString().toUpperCase())
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

    String id = portfolioRequest.getData().iterator().next().getId();

    mockMvc.perform(
        get("/portfolios/{code}/code", "does not exist")
            .with(jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError())
        .andReturn();

    mockMvc.perform(
        get("/portfolios/{code}/code", portfolio.getCode())
            .with(jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    mockMvc.perform(
        get("/portfolios/{id}", id)
            .with(jwt(token).authorities(authorityRoleConverter))
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
        .build();

    Portfolio portfolio = Portfolio.builder()
        .code(UUID.randomUUID().toString().toUpperCase())
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

  @Test
  void is_OwnerHonoured() throws Exception {
    SystemUser userA = SystemUser.builder()
        .build();

    Portfolio portfolio = Portfolio.builder()
        .code(UUID.randomUUID().toString().toUpperCase())
        .name("NZD Portfolio")
        .currency(CurrencyUtils.getCurrency("NZD"))
        .owner(userA)
        .build();

    mockMvc.perform(
        post("/portfolios")
            // No Token
            .content(new ObjectMapper()
                .writeValueAsBytes(PortfolioRequest.builder()
                    .data(Collections.singleton(portfolio))
                    .build()))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isForbidden())
        .andReturn();

    // Add a token and repeat the call
    Jwt tokenA = TokenHelper.getUserToken(userA);
    registerUser(mockMvc, tokenA, userA);

    MvcResult mvcResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt(tokenA).authorities(authorityRoleConverter))
            .content(new ObjectMapper()
                .writeValueAsBytes(PortfolioRequest.builder()
                    .data(Collections.singleton(portfolio))
                    .build()))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    // Logged in user created a Portfolio
    PortfolioRequest portfolioResult = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfolioRequest.class);
    assertThat(portfolioResult.getData())
        .hasSize(1);

    portfolio = portfolioResult.getData().iterator().next();
    assertThat(portfolio).hasNoNullFieldsOrProperties();

    // User A can see the portfolio they created
    mvcResult = mockMvc.perform(
        get("/portfolios/{id}", portfolio.getId())
            .with(jwt(tokenA).authorities(authorityRoleConverter))

    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat(objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfolioRequest.class).getData())
        .hasSize(1);
    // By code, also can
    mvcResult = mockMvc.perform(
        get("/portfolios/{code}/code", portfolio.getCode())
            .with(jwt(tokenA).authorities(authorityRoleConverter))

    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat(objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfolioRequest.class).getData())
        .hasSize(1);

    // All users portfolios
    mvcResult = mockMvc.perform(
        get("/portfolios")
            .with(jwt(tokenA).authorities(authorityRoleConverter))

    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat(objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfolioRequest.class).getData())
        .hasSize(1);

    // User B, while a valid system user, cannot see UserA portfolios even if they know the ID
    SystemUser userB = SystemUser.builder()
        .id("user2")
        .email("user2@testing.com")
        .build();

    Jwt tokenB = TokenHelper.getUserToken(userB);
    registerUser(mockMvc, tokenB, userB);

    mockMvc.perform(
        get("/portfolios/{id}", portfolio.getId())
            .with(jwt(tokenB).authorities(authorityRoleConverter))
    ).andExpect(status().isBadRequest())
        .andReturn();

    mockMvc.perform(
        get("/portfolios/{code}/code", portfolio.getCode())
            .with(jwt(tokenB).authorities(authorityRoleConverter))

    ).andExpect(status().isBadRequest())
        .andReturn();

    // All portfolios
    mvcResult = mockMvc.perform(
        get("/portfolios/")
            .with(jwt(tokenB).authorities(authorityRoleConverter))

    ).andExpect(status().isOk())
        .andReturn();

    assertThat(objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfolioRequest.class).getData())
        .hasSize(0);

  }

  @SneakyThrows
  @Test
  void is_UniqueConstraintInPlace() {
    SystemUser userA = SystemUser.builder()
        .build();

    Portfolio portfolio = Portfolio.builder()
        .code(UUID.randomUUID().toString().toUpperCase())
        .name("NZD Portfolio")
        .currency(CurrencyUtils.getCurrency("NZD"))
        .owner(userA)
        .build();

    Collection<Portfolio> portfolios = new ArrayList<>();
    portfolios.add(portfolio);
    portfolios.add(portfolio); // Code and Owner are the same so, reject

    Jwt token = TokenHelper.getUserToken(userA);
    // Can't create two portfolios with the same code
    MvcResult result = mockMvc.perform(
        post("/portfolios")
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper()
                .writeValueAsBytes(PortfolioRequest.builder()
                    .data(portfolios)
                    .build()))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isBadRequest())
        .andReturn();

    assertThat(result.getResolvedException())
        .isNotNull()
        .isInstanceOfAny(BusinessException.class)
    ;

  }
}

