package com.beancounter.marketdata.integ;

import static com.beancounter.common.utils.AssetUtils.getAssetInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.marketdata.MarketDataBoot;
import com.beancounter.marketdata.assets.AssetService;
import com.beancounter.marketdata.markets.MarketService;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MarketDataBoot.class)
@WebAppConfiguration
@ActiveProfiles("test")
class MarketDataControllerTests {

  private final Asset dummy;
  // Represents dummy after it has been Jackson'ized
  private final Asset dummyJsonAsset;
  private WebApplicationContext wac;
  @MockBean
  private AssetService assetService;
  private MockProviderService mockProviderService;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private MarketDataControllerTests(WebApplicationContext webApplicationContext,
                                    MarketService marketService,
                                    MockProviderService mockProviderService)
      throws JsonProcessingException {
    this.wac = webApplicationContext;
    this.mockProviderService = mockProviderService;
    dummy = AssetUtils.getAsset(
        marketService.getMarket("mock"), "dummy"
    );
    dummyJsonAsset = objectMapper.readValue(objectMapper.writeValueAsString(dummy), Asset.class);
  }

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    Mockito.when(assetService.find(dummy.getId())).thenReturn(dummy);
    Mockito.when(assetService.find(dummy.getMarket().getCode(), dummy.getId())).thenReturn(dummy);
  }

  @Test
  void is_ContextLoaded() {
    assertThat(wac).isNotNull();
  }

  @Test
  @WithMockUser(username = "test-user", roles = {RoleHelper.OAUTH_USER})
  void is_MarketsReturned() throws Exception {
    String json = mockMvc.perform(get("/markets")
        .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(
            status().isOk()
        ).andExpect(
            content().contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andReturn()
        .getResponse().getContentAsString();
    MarketResponse marketResponse = objectMapper.readValue(json, MarketResponse.class);
    assertThat(marketResponse.getData()).isNotNull().isNotEmpty();
  }

  @Test
  @Tag("slow")
  @WithMockUser(username = "test-user", roles = {RoleHelper.OAUTH_USER})
  void is_PriceFormMarketAssetFound() throws Exception {

    String json = mockMvc.perform(
        get("/prices/{marketId}/{assetId}",
            dummy.getMarket().getCode(),
            dummy.getCode()
        )
            .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(
            status().isOk()
        ).andExpect(
            content().contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andReturn().getResponse().getContentAsString();

    PriceResponse priceResponse = objectMapper.readValue(json, PriceResponse.class);
    assertThat(priceResponse.getData()).hasSize(1);

    MarketData marketData = priceResponse.getData().iterator().next();
    assertThat(marketData)
        .hasFieldOrPropertyWithValue("asset", dummyJsonAsset)
        .hasFieldOrPropertyWithValue("open", BigDecimal.valueOf(999.99))
        .hasFieldOrPropertyWithValue("date", mockProviderService.getPriceDate());
  }

  @Test
  @Tag("slow")
  @WithMockUser(username = "test-user", roles = {RoleHelper.OAUTH_USER})
  void is_MdCollectionReturnedForAssets() throws Exception {

    Mockito.when(assetService.find("MOCK", "ASSETCODE"))
        .thenReturn(AssetUtils.getAsset("ASSETCODE", "MOCK"));

    Collection<AssetInput> assetInputs = new ArrayList<>();
    assetInputs.add(
        getAssetInput("MOCK", "ASSETCODE"));

    String json = mockMvc.perform(
        post("/prices")
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .content(objectMapper
                .writeValueAsString(PriceRequest.builder().assets(assetInputs).build())
            ))
        .andExpect(
            status().isOk()
        ).andExpect(
            content().contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andReturn()
        .getResponse()
        .getContentAsString();

    PriceResponse mdResponse = objectMapper.readValue(json, PriceResponse.class);
    assertThat(mdResponse.getData()).hasSize(assetInputs.size());
  }

  @Test
  @Tag("slow")
  @WithMockUser(username = "test-user", roles = {RoleHelper.OAUTH_USER})
  void is_ValuationRequestHydratingAssets() throws Exception {
    String json = mockMvc.perform(get("/prices/{marketId}/{assetId}",
        dummy.getMarket().getCode(),
        dummy.getCode())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
    ).andExpect(
        status().isOk()
    ).andExpect(
        content().contentType(MediaType.APPLICATION_JSON_VALUE)
    ).andReturn()
        .getResponse()
        .getContentAsString();

    PriceResponse priceResponse = objectMapper.readValue(json, PriceResponse.class);
    assertThat(priceResponse.getData()).hasSize(1);

    MarketData marketData = priceResponse.getData().iterator().next();
    assertThat(marketData)
        .hasFieldOrPropertyWithValue("asset", dummyJsonAsset)
        .hasFieldOrPropertyWithValue("open", BigDecimal.valueOf(999.99))
        .hasFieldOrPropertyWithValue("date", mockProviderService.getPriceDate());


  }
}

