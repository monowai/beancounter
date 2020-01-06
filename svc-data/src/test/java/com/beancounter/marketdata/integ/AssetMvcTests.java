package com.beancounter.marketdata.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
  void is_PutAssetCreating() throws Exception {
    Asset asset = AssetUtils.getAsset("MyCode", "MOCK");

    MvcResult mvcResult = mockMvc.perform(
        put("/assets/")
            .content(objectMapper.writeValueAsBytes(asset))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    Asset putAsset = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), Asset.class);

    // marketCode is for persistence only,  Clients should rely on the
    //   hydrated Market object
    assertThat(putAsset)
        .isNotNull()
        .hasFieldOrProperty("id")
        .hasFieldOrProperty("market")
        .hasFieldOrPropertyWithValue("code", asset.getCode().toUpperCase())
        .hasFieldOrPropertyWithValue("market.code", asset.getMarketCode().toUpperCase())
        .hasFieldOrPropertyWithValue("marketCode", null)
        .hasFieldOrProperty("id")

    ;
    // Attempt to update the name
    putAsset.setName("Other Name");

    // Calling PUT the second time should not update and will return the same asset Id
    mvcResult = mockMvc.perform(
        put("/assets/")
            .content(objectMapper.writeValueAsBytes(putAsset))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    Asset secondPut = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), Asset.class);

    // Name should remain as null as can't PUT same value twice
    assertThat(secondPut)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", null)
        .isEqualToComparingOnlyGivenFields(putAsset,
            "code", "market");
  }

  @Test
  void is_PutAndPostAsset() throws Exception {
    Asset asset = AssetUtils.getAsset("MyCodeX", "MOCK");

    MvcResult mvcResult = mockMvc.perform(
        put("/assets/")
            .content(objectMapper.writeValueAsBytes(asset))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    Asset putAsset = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), Asset.class);

    assertThat(putAsset)
        .isNotNull()
        .hasFieldOrProperty("id")
        .hasFieldOrProperty("market")

    ;
    // Attempt to change the name
    putAsset.setName("Other Name");

    // Calling PUT the second time should not update and will return the same asset Id
    mvcResult = mockMvc.perform(
        post("/assets/")
            .content(objectMapper.writeValueAsBytes(putAsset))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    Asset updatedAsset = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), Asset.class);

    // Name should remain as null as can't PUT same value twice
    assertThat(updatedAsset)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "Other Name")
        .isEqualToComparingOnlyGivenFields(putAsset,
            "code", "market");
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
