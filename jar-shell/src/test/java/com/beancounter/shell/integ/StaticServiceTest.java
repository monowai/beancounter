package com.beancounter.shell.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.shell.config.ShareSightConfig;
import com.beancounter.shell.service.AssetService;
import com.beancounter.shell.service.PortfolioService;
import com.beancounter.shell.service.StaticService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@ActiveProfiles("test")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@SpringBootTest(classes = {ShareSightConfig.class})
public class StaticServiceTest {
  @Autowired
  private StaticService staticService;

  @Autowired
  private PortfolioService portfolioService;

  @Autowired
  private AssetService assetService;

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

  @Test
  void is_MarketIllegalArgumentsThrowing() {
    assertThrows(BusinessException.class, () ->
        staticService.resolveMarket(null, assetService, assetService.staticService));
    assertThrows(BusinessException.class, () ->
        staticService.resolveMarket("ERR", assetService, assetService.staticService));
  }
}
