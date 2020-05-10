package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.PortfolioResponse;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.PortfolioUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class TestPortfolio {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void is_PortfolioResultsSerializing() throws Exception {

    PortfolioInput portfolio = PortfolioUtils.getPortfolioInput("Test");
    Collection<PortfolioInput> portfolioInputs = new ArrayList<>();
    portfolioInputs.add(portfolio);
    PortfoliosRequest portfoliosRequest = PortfoliosRequest.builder().data(portfolioInputs).build();
    String json = objectMapper.writeValueAsString(portfoliosRequest);

    assertThat(objectMapper.readValue(json, PortfoliosRequest.class))
        .isEqualToComparingFieldByField(portfoliosRequest);

    Collection<Portfolio> portfolios = new ArrayList<>();
    portfolios.add(PortfolioUtils.getPortfolio("TEST"));

    PortfoliosResponse portfoliosResponse = PortfoliosResponse.builder()
        .data(portfolios).build();

    json = objectMapper.writeValueAsString(portfoliosResponse);

    assertThat(objectMapper.readValue(json, PortfoliosResponse.class))
        .isEqualToComparingFieldByField(portfoliosResponse);

  }

  @Test
  void is_PortfolioResponseSerializing() throws Exception {
    PortfolioResponse portfolioResponse = PortfolioResponse.builder()
        .data(PortfolioUtils.getPortfolio("TEST"))
        .build();
    String json = objectMapper.writeValueAsString(portfolioResponse);
    assertThat(objectMapper.readValue(json, PortfolioResponse.class))
        .isEqualToComparingFieldByField(portfolioResponse);

  }
}
