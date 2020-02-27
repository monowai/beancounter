package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.PortfolioRequest;
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
    PortfolioRequest portfolioRequest = PortfolioRequest.builder().data(portfolios).build();
    String json = objectMapper.writeValueAsString(portfolioRequest);

    assertThat(objectMapper.readValue(json, PortfolioRequest.class))
        .isEqualToComparingFieldByField(portfolioRequest);
  }
}
