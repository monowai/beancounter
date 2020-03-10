package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.PortfolioInput;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.utils.PortfolioUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class TestPortfolio {
  @Test
  void is_PortfolioResultsSerializing() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    PortfolioInput portfolio = PortfolioUtils.getPortfolioInput("Test");
    Collection<PortfolioInput> portfolios = new ArrayList<>();
    portfolios.add(portfolio);
    PortfoliosRequest portfoliosRequest = PortfoliosRequest.builder().data(portfolios).build();
    String json = objectMapper.writeValueAsString(portfoliosRequest);

    assertThat(objectMapper.readValue(json, PortfoliosRequest.class))
        .isEqualToComparingFieldByField(portfoliosRequest);
  }
}
