package com.beancounter.marketdata.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.auth.server.AuthorityRoleConverter;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.contracts.AssetUpdateResponse;
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
class TestAssetMvc {

  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private WebApplicationContext context;
  private MockMvc mockMvc;

  private AuthorityRoleConverter authorityRoleConverter = new AuthorityRoleConverter();
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
    token = TokenUtils.getUserToken(user);
    TestRegistrationMvc.registerUser(mockMvc, token, user);

  }

  @Test
  void is_AssetCreationAndFindByWorking() throws Exception {

    Asset firstAsset = AssetUtils.getAsset("MyCode", "MOCK");
    Asset secondAsset = AssetUtils.getAsset("Second", "MOCK");
    AssetRequest assetRequest = AssetRequest.builder()
        .data(AssetUtils.toKey(firstAsset), firstAsset)
        .data(AssetUtils.toKey(secondAsset), secondAsset)
        .build();

    MvcResult mvcResult = mockMvc.perform(
        post("/assets/")
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(objectMapper.writeValueAsBytes(assetRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetUpdateResponse assetUpdateResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), AssetUpdateResponse.class);

    assertThat(assetUpdateResponse.getData()).hasSize(2);


    // marketCode is for persistence only,  Clients should rely on the
    //   hydrated Market object

    assertThat(assetUpdateResponse.getData().get(AssetUtils.toKey(firstAsset)))
        .isNotNull()
        .hasFieldOrProperty("id")
        .hasFieldOrProperty("market")
        .hasFieldOrPropertyWithValue("code", firstAsset.getCode().toUpperCase())
        .hasFieldOrPropertyWithValue("marketCode", null)
        .hasFieldOrProperty("id");

    assertThat(assetUpdateResponse.getData().get(AssetUtils.toKey(secondAsset)))
        .isNotNull()
        .hasFieldOrProperty("id")
        .hasFieldOrProperty("market")
        .hasFieldOrPropertyWithValue("code", secondAsset.getCode().toUpperCase())
        .hasFieldOrPropertyWithValue("marketCode", null)
        .hasFieldOrProperty("id");

    Asset asset = assetUpdateResponse.getData().get(AssetUtils.toKey(secondAsset));
    mvcResult = mockMvc.perform(
        get("/assets/{assetId}", asset.getId())
            .with(jwt(token).authorities(authorityRoleConverter))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    // Find by Primary Key
    AssetResponse assetResponse = objectMapper.readValue(
        mvcResult.getResponse().getContentAsString(), AssetResponse.class
    );
    assertThat(assetResponse.getData())
        .isEqualToComparingFieldByField(asset);

    // By Market/Asset
    mvcResult = mockMvc.perform(
        get("/assets/{marketCode}/{assetCode}",
            asset.getMarket().getCode(), asset.getCode())
            .with(jwt(token).authorities(authorityRoleConverter))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat(objectMapper.readValue(mvcResult.getResponse().getContentAsString(), Asset.class))
        .isEqualToComparingFieldByField(asset);

  }

  @Test
  void is_PostSameAssetTwiceBehaving() throws Exception {
    Asset asset = AssetUtils.getAsset("MyCodeX", "MOCK");
    AssetRequest assetRequest = AssetRequest.builder()
        .data(AssetUtils.toKey(asset), asset)
        .build();

    MvcResult mvcResult = mockMvc.perform(
        post("/assets/")
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(objectMapper.writeValueAsBytes(assetRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetUpdateResponse assetUpdateResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), AssetUpdateResponse.class);

    Asset createdAsset = assetUpdateResponse.getData().get(AssetUtils.toKey(asset));
    assertThat(createdAsset)
        .isNotNull()
        .hasFieldOrProperty("id")
        .hasFieldOrProperty("market");

    // Send it a second time, should not change
    asset.setName("Random Change");
    asset.setId(null);
    assetRequest = AssetRequest.builder()
        .data(AssetUtils.toKey(asset), asset)
        .build();

    mvcResult = mockMvc.perform(
        post("/assets/")
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(objectMapper.writeValueAsBytes(assetRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assetUpdateResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), AssetUpdateResponse.class);

    Asset updatedAsset = assetUpdateResponse.getData().get(AssetUtils.toKey(asset));
    assertThat(updatedAsset).isEqualToComparingFieldByField(createdAsset);
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

    result = mockMvc.perform(
        get("/assets/{assetId}", "doesn't exist")
            .with(jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError());

    assertThat(result.andReturn().getResolvedException())
        .isNotNull()
        .isInstanceOfAny(BusinessException.class);
  }

}
