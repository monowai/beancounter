package com.beancounter.marketdata.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.TrnId;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.CurrencyUtils;
import com.beancounter.marketdata.markets.MarketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collections;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
public class TrnMvcTest {
  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private WebApplicationContext wac;

  @Autowired
  private MarketService marketService;

  private MockMvc mockMvc;

  @Autowired
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();

  }

  @Test
  void is_PersistAndRetrieve() throws Exception {

    Market nasdaq = marketService.getMarket("NASDAQ");

    AssetRequest assetRequest = AssetRequest.builder()
        .asset("msft", AssetUtils.getAsset("MSFT", nasdaq))
        .build();

    Asset msft = asset(assetRequest);

    Portfolio portfolio = portfolio(Portfolio.builder()
        .code("Twix")
        .name("NZD Portfolio")
        .currency(CurrencyUtils.getCurrency("NZD"))
        .build());

    TrnId trnId = TrnId.builder()
        .batch(999)
        .provider("1a0OYzNj4Ru2zGS76EimzdjQm9URHQbuhwxvDLGJ8ur33")
        .id(999).build();

    Trn trn = Trn.builder()
        .portfolioId(portfolio.getId())
        .asset(msft)
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(msft.getMarket().getCurrency())
        .tradePortfolioRate(BigDecimal.ONE)
        .id(trnId)
        .build();

    TrnRequest trnRequest = TrnRequest.builder()
        .trn(trn)
        .porfolioId(portfolio.getId())
        .build();

    MvcResult mvcResult = mockMvc.perform(
        post("/trns")
            .content(new ObjectMapper().writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    TrnResponse trnResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getTrns()).isNotEmpty();
    assertThat(trnResponse.getPortfolios()).isNotEmpty().hasSize(1);

    String portfolioId = trnResponse.getTrns().iterator().next().getPortfolioId();
    assertThat(trnResponse.getPortfolios())
        .isNotEmpty()
        .hasAtLeastOneElementOfType(Portfolio.class);
    assertThat(portfolio).isEqualTo(trnResponse.getPortfolios().iterator().next());

    // Find by Portfolio
    mvcResult = mockMvc.perform(
        get("/trns/{portfolioId}", portfolioId)
            .content(new ObjectMapper().writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    trnResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getTrns()).isNotEmpty();
    assertThat(trnResponse.getPortfolios()).isNotEmpty().hasSize(1);


    // Find by PrimaryKey
    mvcResult = mockMvc.perform(
        get("/trns/{portfolioId}/{provider}/{batch}/{id}",
            portfolioId, trnId.getProvider(), trnId.getBatch(), trnId.getId())
            .content(new ObjectMapper().writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    trnResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getTrns()).isNotEmpty();
    assertThat(trnResponse.getPortfolios()).isNotEmpty().hasSize(1);

  }

  private Portfolio portfolio(Portfolio portfolio) throws Exception {

    PortfolioRequest createRequest = PortfolioRequest.builder()
        .data(Collections.singleton(portfolio))
        .build();

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            .content(new ObjectMapper().writeValueAsBytes(createRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfolioRequest portfolioResponse = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfolioRequest.class);
    return portfolioResponse.getData().iterator().next();
  }

  private Asset asset(AssetRequest assetRequest) throws Exception {
    MvcResult mvcResult = mockMvc.perform(
        post("/assets/")
            .content(objectMapper.writeValueAsBytes(assetRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetResponse assetResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);

    return assetResponse.getAssets().values().iterator().next();
  }
}
