package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.integ.TestRegistrationMvc.registerUser;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
@Slf4j
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
    SystemUser user = SysUserUtils.getSystemUser();

    PortfolioInput portfolio = PortfolioInput.builder()
        .code(UUID.randomUUID().toString().toUpperCase())
        .name("NZD Portfolio")
        .currency("NZD")
        .build();

    Jwt token = TokenUtils.getUserToken(user);
    registerUser(mockMvc, token);

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            // Mocking does not use the JwtRoleConverter configured in ResourceServerConfig
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper()
                .writeValueAsBytes(PortfoliosRequest.builder()
                    .data(Collections.singleton(portfolio))
                    .build()))
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
        .isEqualToComparingFieldByField(portfolioResponseByCode);

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
    PortfolioInput portfolioInput = PortfolioInput.builder()
        .code(UUID.randomUUID().toString().toUpperCase())
        .name("NZD Portfolio")
        .currency("NZD")
        .build();

    portfolios.add(portfolioInput);
    PortfoliosRequest createRequest = PortfoliosRequest.builder()
        .data(portfolios)
        .build();

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(createRequest))
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

    PortfolioInput portfolioInput = PortfolioInput.builder()
        .code(UUID.randomUUID().toString())
        .name("NZD Portfolio")
        .currency("NZD")
        .build();

    mockMvc.perform(
        post("/portfolios")
            // No Token
            .content(new ObjectMapper()
                .writeValueAsBytes(PortfoliosRequest.builder()
                    .data(Collections.singleton(portfolioInput))
                    .build()))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isForbidden())
        .andReturn();

    // Add a token and repeat the call
    Jwt tokenA = TokenUtils.getUserToken(userA);
    registerUser(mockMvc, tokenA);

    MvcResult mvcResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt().jwt(tokenA).authorities(authorityRoleConverter))
            .content(new ObjectMapper()
                .writeValueAsBytes(PortfoliosRequest.builder()
                    .data(Collections.singleton(portfolioInput))
                    .build()))
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
    SystemUser userB = SystemUser.builder()
        .id("user2")
        .email("user2@testing.com")
        .build();

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
  @SneakyThrows
  void is_DeletePortfolio() {
    PortfolioInput portfolioInput = PortfolioInput.builder()
        .code(UUID.randomUUID().toString())
        .name("NZD Portfolio")
        .currency("NZD")
        .build();

    SystemUser userA = SysUserUtils.getSystemUser();

    // Add a token and repeat the call
    Jwt token = TokenUtils.getUserToken(userA);
    registerUser(mockMvc, token);

    MvcResult mvcResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper()
                .writeValueAsBytes(PortfoliosRequest.builder()
                    .data(Collections.singleton(portfolioInput))
                    .build()))
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

  @SneakyThrows
  @Test
  void is_UniqueConstraintInPlace() {
    PortfolioInput portfolio = PortfolioInput.builder()
        .code("Code")
        .name("NZD Portfolio")
        .currency("NZD")
        .build();

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    portfolios.add(portfolio);
    portfolios.add(portfolio); // Code and Owner are the same so, reject

    SystemUser userA = SysUserUtils.getSystemUser();
    Jwt token = TokenUtils.getUserToken(userA);
    registerUser(mockMvc, token);
    // Can't create two portfolios with the same code
    MvcResult result = mockMvc.perform(
        post("/portfolios")
            .with(
                jwt().jwt(token).authorities(authorityRoleConverter)
            ).content(new ObjectMapper()
            .writeValueAsBytes(PortfoliosRequest.builder()
                .data(portfolios)
                .build()))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isBadRequest())
        .andReturn();

    assertThat(result.getResolvedException())
        .isNotNull()
        .isInstanceOfAny(DataIntegrityViolationException.class)
    ;

  }

  @SneakyThrows
  @Test
  void is_UnregisteredUserRejected() {
    PortfolioInput portfolio = PortfolioInput.builder()
        .code("Code")
        .name("NZD Portfolio")
        .currency("NZD")
        .build();

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    portfolios.add(portfolio);

    SystemUser userA = SysUserUtils.getSystemUser();
    Jwt token = TokenUtils.getUserToken(userA);
    // registerUser(mockMvc, token);
    // Authenticated, but unregistered user can't create portfolios
    MvcResult result = mockMvc.perform(
        post("/portfolios")
            .with(
                jwt().jwt(token).authorities(authorityRoleConverter)
            ).content(new ObjectMapper()
            .writeValueAsBytes(PortfoliosRequest.builder()
                .data(portfolios)
                .build()))
            .contentType(MediaType.APPLICATION_JSON)

    ).andExpect(status().isForbidden())
        .andReturn();

    assertThat(result.getResolvedException())
        .isNotNull()
        .isInstanceOfAny(ForbiddenException.class)
    ;

  }

  @SneakyThrows
  @Test
  void is_UpdatePortfolioWorking() {
    SystemUser user = SysUserUtils.getSystemUser();

    Jwt token = TokenUtils.getUserToken(user);
    registerUser(mockMvc, token);

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    PortfolioInput portfolioInput = PortfolioInput.builder()
        .code(UUID.randomUUID().toString().toUpperCase())
        .name("NZD Portfolio")
        .currency("NZD")
        .build();

    portfolios.add(portfolioInput);
    PortfoliosRequest createRequest = PortfoliosRequest.builder()
        .data(portfolios)
        .build();

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(createRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfoliosResponse portfoliosResponse = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfoliosResponse.class);

    Portfolio portfolio = portfoliosResponse.getData().iterator().next();

    PortfolioInput updateTo = PortfolioInput.builder()
        .code("123")
        .name("Mikey")
        .base("SGD")
        .currency("SGD")
        .build();

    MvcResult patchResult = mockMvc.perform(
        patch("/portfolios/{id}", portfolio.getId())
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(updateTo))
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

