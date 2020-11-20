package com.beancounter.marketdata.integ;

import static com.beancounter.common.utils.BcJson.getObjectMapper;
import static com.beancounter.marketdata.utils.RegistrationUtils.registerUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.auth.server.AuthorityRoleConverter;
import com.beancounter.common.contracts.PortfolioResponse;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.exception.ForbiddenException;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.marketdata.utils.SysUserUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(PortfolioMvcTests.class);
  private final ObjectMapper objectMapper = getObjectMapper();
  private final AuthorityRoleConverter authorityRoleConverter = new AuthorityRoleConverter();
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
  void is_findingByIdCode() throws Exception {
    SystemUser user = SysUserUtils.getSystemUser();

    PortfolioInput portfolio = new PortfolioInput(
        UUID.randomUUID().toString().toUpperCase(),
        "NZD Portfolio",
        "NZD",
        "USD");

    Jwt token = TokenUtils.getUserToken(user);
    registerUser(mockMvc, token);

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            // Mocking does not use the JwtRoleConverter configured in ResourceServerConfig
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(getObjectMapper()
                .writeValueAsBytes(new PortfoliosRequest(Collections.singleton(portfolio))))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfoliosResponse portfolios = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfoliosResponse.class);

    assertThat(portfolios)
        .isNotNull()
        .hasFieldOrProperty("data");

    assertThat(portfolios.getData())
        .hasSize(1);

    // Assert not found
    mockMvc.perform(
        get("/portfolios/code/{code}", "does not exist")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError())
        .andReturn();

    // Found by code
    String result = mockMvc.perform(
        get("/portfolios/code/{code}", portfolio.getCode())
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(
        status()
            .isOk())
        .andExpect(content()
            .contentType(MediaType.APPLICATION_JSON))
        .andReturn().getResponse().getContentAsString();

    PortfolioResponse portfolioResponseByCode = objectMapper
        .readValue(result, PortfolioResponse.class);

    assertThat(portfolioResponseByCode)
        .isNotNull()
        .hasNoNullFieldsOrProperties();

    mockMvc.perform(
        get("/portfolios/{id}", "invalidId")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError())
        .andReturn();

    result = mockMvc.perform(
        get("/portfolios/{id}", portfolioResponseByCode.getData().getId())
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn().getResponse().getContentAsString();

    assertThat(objectMapper.readValue(result, PortfolioResponse.class))
        .usingRecursiveComparison().isEqualTo(portfolioResponseByCode);

    MvcResult mvcResult = mockMvc.perform(
        get("/portfolios")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfoliosResponse found = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfoliosResponse.class);

    assertThat(found.getData()).isNotEmpty();

  }

  @Test
  void is_persistAndFindPortfoliosWorking() throws Exception {
    SystemUser user = SysUserUtils.getSystemUser();

    Jwt token = TokenUtils.getUserToken(user);
    registerUser(mockMvc, token);

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    PortfolioInput portfolioInput = new PortfolioInput(
        UUID.randomUUID().toString().toUpperCase(),
        "NZD Portfolio",
        "NZD",
        "USD");

    portfolios.add(portfolioInput);
    PortfoliosRequest createRequest = new PortfoliosRequest(portfolios);

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(getObjectMapper().writeValueAsBytes(createRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfoliosResponse portfoliosResponse = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfoliosResponse.class);

    assertThat(portfoliosResponse)
        .isNotNull()
        .hasFieldOrProperty("data");

    assertThat(portfoliosResponse.getData())
        .hasSize(1);

    portfoliosResponse.getData().forEach(foundPortfolio -> assertThat(foundPortfolio)
        .hasFieldOrProperty("id")
        .hasFieldOrPropertyWithValue("code", portfolioInput.getCode())
        .hasFieldOrPropertyWithValue("name",
            portfolioInput.getCurrency() + " Portfolio")
        .hasFieldOrPropertyWithValue("currency.code", portfolioInput.getCurrency())
        .hasFieldOrProperty("owner")
        .hasFieldOrProperty("base"));

  }

  @Test
  void is_OwnerHonoured() throws Exception {
    SystemUser userA = SysUserUtils.getSystemUser();

    PortfolioInput portfolioInput = new PortfolioInput(
        UUID.randomUUID().toString().toUpperCase(),
        "NZD Portfolio",
        "NZD",
        "USD");

    mockMvc.perform(
        post("/portfolios")
            // No Token
            .content(getObjectMapper()
                .writeValueAsBytes(new PortfoliosRequest(Collections.singleton(portfolioInput))))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isForbidden())
        .andReturn();

    // Add a token and repeat the call
    Jwt tokenA = TokenUtils.getUserToken(userA);
    registerUser(mockMvc, tokenA);

    MvcResult mvcResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt().jwt(tokenA).authorities(authorityRoleConverter))
            .content(getObjectMapper()
                .writeValueAsBytes(new PortfoliosRequest(Collections.singleton(portfolioInput))))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    // User A creates a Portfolio
    PortfoliosResponse portfoliosResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfoliosResponse.class);
    assertThat(portfoliosResponse.getData())
        .hasSize(1);
    Portfolio portfolio = portfoliosResponse.getData().iterator().next();
    assertThat(portfolio).hasNoNullFieldsOrProperties();
    assertThat(portfolio.getOwner()).hasFieldOrPropertyWithValue("id", userA.getId());
    log.debug("{}", portfolio.getOwner());

    // User A can see the portfolio they created
    mvcResult = mockMvc.perform(
        get("/portfolios/{id}", portfolio.getId())
            .with(jwt().jwt(tokenA).authorities(authorityRoleConverter))

    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat(objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfolioResponse.class)
        .getData()).hasNoNullFieldsOrProperties();

    // By code, also can
    mvcResult = mockMvc.perform(
        get("/portfolios/code/{code}", portfolioInput.getCode())
            .with(jwt().jwt(tokenA).authorities(authorityRoleConverter))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat(objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfolioResponse.class)
        .getData())
        .hasNoNullFieldsOrProperties();

    // All users portfolios
    mvcResult = mockMvc.perform(
        get("/portfolios")
            .with(jwt().jwt(tokenA).authorities(authorityRoleConverter))

    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat(objectMapper
        .readValue(
            mvcResult.getResponse().getContentAsString(), PortfoliosResponse.class).getData())
        .hasSize(1);

    // User B, while a valid system user, cannot see UserA portfolios even if they know the ID
    SystemUser userB = new SystemUser("user2", "user2@testing.com");

    Jwt tokenB = TokenUtils.getUserToken(userB);
    registerUser(mockMvc, tokenB);

    mockMvc.perform(
        get("/portfolios/{id}", portfolio.getId())
            .with(jwt().jwt(tokenB).authorities(authorityRoleConverter))
    ).andExpect(status().isBadRequest())
        .andReturn();

    mockMvc.perform(
        get("/portfolios/code/{code}", portfolio.getCode())
            .with(jwt().jwt(tokenB).authorities(authorityRoleConverter))

    ).andExpect(status().isBadRequest())
        .andReturn();

    // All portfolios
    mvcResult = mockMvc.perform(
        get("/portfolios/")
            .with(jwt().jwt(tokenB).authorities(authorityRoleConverter))

    ).andExpect(status().isOk())
        .andReturn();

    assertThat(objectMapper
        .readValue(
            mvcResult.getResponse().getContentAsString(), PortfoliosResponse.class).getData())
        .hasSize(0);
  }

  @Test
  void is_DeletePortfolio() throws Exception {
    PortfolioInput portfolioInput = new PortfolioInput(
        UUID.randomUUID().toString().toUpperCase(),
        "NZD Portfolio",
        "NZD",
        "USD");

    SystemUser userA = SysUserUtils.getSystemUser();

    // Add a token and repeat the call
    Jwt token = TokenUtils.getUserToken(userA);
    registerUser(mockMvc, token);

    MvcResult mvcResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(getObjectMapper()
                .writeValueAsBytes(new PortfoliosRequest(Collections.singleton(portfolioInput))))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    // User A created a Portfolio
    PortfoliosResponse portfoliosResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfoliosResponse.class);
    assertThat(portfoliosResponse.getData())
        .hasSize(1);
    Portfolio portfolio = portfoliosResponse.getData().iterator().next();

    mvcResult = mockMvc.perform(
        delete("/portfolios/{id}", portfolio.getId())
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isOk())
        .andReturn();

    assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo("ok");
  }

  @Test
  void is_UniqueConstraintInPlace() throws Exception {
    PortfolioInput portfolioInput = new PortfolioInput(
        UUID.randomUUID().toString().toUpperCase(),
        "NZD Portfolio",
        "NZD",
        "USD");

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    portfolios.add(portfolioInput);
    portfolios.add(portfolioInput); // Code and Owner are the same so, reject

    SystemUser userA = SysUserUtils.getSystemUser();
    Jwt token = TokenUtils.getUserToken(userA);
    registerUser(mockMvc, token);
    // Can't create two portfolios with the same code
    MvcResult result = mockMvc.perform(
        post("/portfolios")
            .with(
                jwt().jwt(token).authorities(authorityRoleConverter)
            ).content(getObjectMapper()
            .writeValueAsBytes(new PortfoliosRequest(portfolios)))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isBadRequest())
        .andReturn();

    assertThat(result.getResolvedException())
        .isNotNull()
        .isInstanceOfAny(DataIntegrityViolationException.class)
    ;

  }

  @Test
  void is_UnregisteredUserRejected() throws Exception {
    PortfolioInput portfolioInput = new PortfolioInput(
        UUID.randomUUID().toString().toUpperCase(),
        "NZD Portfolio",
        "NZD",
        "USD");

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    portfolios.add(portfolioInput);

    SystemUser userA = SysUserUtils.getSystemUser();
    Jwt token = TokenUtils.getUserToken(userA);
    // registerUser(mockMvc, token);
    // Authenticated, but unregistered user can't create portfolios
    MvcResult result = mockMvc.perform(
        post("/portfolios")
            .with(
                jwt().jwt(token).authorities(authorityRoleConverter)
            ).content(getObjectMapper()
            .writeValueAsBytes(new PortfoliosRequest(portfolios)))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isForbidden())
        .andReturn();

    assertThat(result.getResolvedException())
        .isNotNull()
        .isInstanceOfAny(ForbiddenException.class)
    ;

  }

  @Test
  void is_UpdatePortfolioWorking() throws Exception {
    SystemUser user = SysUserUtils.getSystemUser();

    Jwt token = TokenUtils.getUserToken(user);
    registerUser(mockMvc, token);

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    PortfolioInput portfolioInput = new PortfolioInput(
        UUID.randomUUID().toString().toUpperCase(),
        "NZD Portfolio",
        "NZD",
        "USD");

    portfolios.add(portfolioInput);
    PortfoliosRequest createRequest = new PortfoliosRequest(portfolios);

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(getObjectMapper().writeValueAsBytes(createRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfoliosResponse portfoliosResponse = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfoliosResponse.class);

    Portfolio portfolio = portfoliosResponse.getData().iterator().next();

    PortfolioInput updateTo = new PortfolioInput(
        "123", "Mikey", "SGD", "USD");

    MvcResult patchResult = mockMvc.perform(
        patch("/portfolios/{id}", portfolio.getId())
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(getObjectMapper().writeValueAsBytes(updateTo))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfolioResponse patchResponse = objectMapper
        .readValue(patchResult.getResponse().getContentAsString(), PortfolioResponse.class);

    // ID and SystemUser are immutable:
    assertThat(patchResponse.getData())
        .hasFieldOrPropertyWithValue("id", portfolio.getId())
        .hasFieldOrPropertyWithValue("name", updateTo.getName())
        .hasFieldOrPropertyWithValue("code", updateTo.getCode())
        .hasFieldOrPropertyWithValue("currency.code", updateTo.getCurrency())
        .hasFieldOrPropertyWithValue("base.code", updateTo.getBase())
        .hasFieldOrPropertyWithValue("owner.id", portfolio.getOwner().getId());

  }
}

