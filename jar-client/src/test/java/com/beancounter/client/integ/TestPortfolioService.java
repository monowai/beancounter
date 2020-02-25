package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.client.ClientConfig;
import com.beancounter.client.PortfolioService;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;

@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@ImportAutoConfiguration(ClientConfig.class)
@SpringBootTest(classes = ClientConfig.class)
public class TestPortfolioService {
  @Autowired
  private PortfolioService portfolioService;

  @Test
  void is_PortfolioFinders() {
    Portfolio portfolioByCode = portfolioService.getPortfolioByCode("TEST");
    Portfolio portfolioById = portfolioService.getPortfolioById("TEST");

    assertThat(portfolioByCode).isEqualToComparingFieldByField(portfolioById);
  }

  @Test
  void is_PortfolioIllegalArgumentsThrowing() {
    assertThrows(BusinessException.class, () ->
        portfolioService.getPortfolioByCode(null));
    assertThrows(BusinessException.class, () ->
        portfolioService.getPortfolioByCode("IA"));
    assertThrows(BusinessException.class, () ->
        portfolioService.getPortfolioById(null));
  }


}
