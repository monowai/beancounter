package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.PortfolioResponse;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.PortfolioUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class TestPortfolio {
  @Test
  void is_PortfolioResultsSerializing() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    Portfolio portfolio = PortfolioUtils.getPortfolio("Test");
    Collection<Portfolio> portfolios = new ArrayList<>();
    portfolios.add(portfolio);
    PortfolioResponse portfolioResponse = PortfolioResponse.builder().data(portfolios).build();
    String json = objectMapper.writeValueAsString(portfolioResponse);

    assertThat(objectMapper.readValue(json, PortfolioResponse.class))
        .isEqualToComparingFieldByField(portfolioResponse);
  }
}
