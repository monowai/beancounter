package com.beancounter.marketdata.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.utils.AssetUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
  private WebApplicationContext wac;
  private MockMvc mockMvc;

  @Autowired
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();

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
    // Attempt to change the name

    //ToDo: Update assets
    //    putAsset.setName("Other Name");
    //
    //    assetRequest = AssetRequest.builder()
    //        .asset(AssetUtils.toKey(putAsset), putAsset)
    //        .build();
    //
    //    // Calling PUT the second time should not update and will return the same asset Id
    //    mvcResult = mockMvc.perform(
    //        post("/assets/")
    //            .content(objectMapper.writeValueAsBytes(assetRequest))
    //            .contentType(MediaType.APPLICATION_JSON)
    //    ).andExpect(status().isOk())
    //        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    //        .andReturn();
    //
    //    assetResponse = objectMapper
    //        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);
    //
    //    // Name should remain as null as can't PUT same value twice
    //    assertThat(assetResponse.getAssets().get(AssetUtils.toKey(putAsset)))
    //        .isNotNull()
    //        .hasFieldOrPropertyWithValue("name", "Other Name")
    //        .isEqualToComparingOnlyGivenFields(putAsset,
    //            "code", "market");
  }

  @Test
  void is_MissingAssetBadRequest() throws Exception {
    ResultActions result = mockMvc.perform(
        get("/assets/twee/blah")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError());

    assertThat(result.andReturn().getResolvedException())
        .isNotNull()
        .isInstanceOfAny(BusinessException.class);
  }

}
