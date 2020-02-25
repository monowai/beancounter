package com.beancounter.marketdata.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.AuthorityRoleConverter;
import com.beancounter.auth.JwtRoleConverter;
import com.beancounter.auth.TokenHelper;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.AssetUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AssetMvcTests {

  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private WebApplicationContext context;
  private MockMvc mockMvc;

  private AuthorityRoleConverter authorityRoleConverter
      = new AuthorityRoleConverter(new JwtRoleConverter());
  private Jwt token;

  @Autowired
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();

    // Setup a user account
    SystemUser user = SystemUser.builder()
        .id("user")
        .email("user@testing.com")
        .build();
    token = TokenHelper.getUserToken(user);
    TestRegistrationMvc.registerUser(mockMvc, token, user);

  }

  @Test
  void is_PostAssetCollectionCreating() throws Exception {

    Asset firstAsset = AssetUtils.getAsset("MyCode", "MOCK");
    Asset secondAsset = AssetUtils.getAsset("Second", "MOCK");
    AssetRequest assetRequest = AssetRequest.builder()
        .asset(AssetUtils.toKey(firstAsset), firstAsset)
        .asset(AssetUtils.toKey(secondAsset), secondAsset)
        .build();

    MvcResult mvcResult = mockMvc.perform(
        post("/assets/")
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(objectMapper.writeValueAsBytes(assetRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetResponse assetResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);

    assertThat(assetResponse.getAssets()).hasSize(2);


    // marketCode is for persistence only,  Clients should rely on the
    //   hydrated Market object

    assertThat(assetResponse.getAssets().get(AssetUtils.toKey(firstAsset)))
        .isNotNull()
        .hasFieldOrProperty("id")
        .hasFieldOrProperty("market")
        .hasFieldOrPropertyWithValue("code", firstAsset.getCode().toUpperCase())
        .hasFieldOrPropertyWithValue("market.code", firstAsset.getMarketCode().toUpperCase())
        .hasFieldOrPropertyWithValue("marketCode", null)
        .hasFieldOrProperty("id");

    assertThat(assetResponse.getAssets().get(AssetUtils.toKey(secondAsset)))
        .isNotNull()
        .hasFieldOrProperty("id")
        .hasFieldOrProperty("market")
        .hasFieldOrPropertyWithValue("code", secondAsset.getCode().toUpperCase())
        .hasFieldOrPropertyWithValue("market.code", secondAsset.getMarketCode().toUpperCase())
        .hasFieldOrPropertyWithValue("marketCode", null)
        .hasFieldOrProperty("id");


  }

  @Test
  void is_UpdateAssetWorking() throws Exception {
    Asset asset = AssetUtils.getAsset("MyCodeX", "MOCK");
    AssetRequest assetRequest = AssetRequest.builder()
        .asset(AssetUtils.toKey(asset), asset)
        .build();

    MvcResult mvcResult = mockMvc.perform(
        post("/assets/")
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(objectMapper.writeValueAsBytes(assetRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetResponse assetResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);

    Asset putAsset = assetResponse.getAssets().get(AssetUtils.toKey(asset));
    assertThat(putAsset)
        .isNotNull()
        .hasFieldOrProperty("id")
        .hasFieldOrProperty("market");
  }

  @Test
  void is_MissingAssetBadRequest() throws Exception {
    ResultActions result = mockMvc.perform(
        get("/assets/twee/blah")
            .with(jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError());

    assertThat(result.andReturn().getResolvedException())
        .isNotNull()
        .isInstanceOfAny(BusinessException.class);
  }

}
