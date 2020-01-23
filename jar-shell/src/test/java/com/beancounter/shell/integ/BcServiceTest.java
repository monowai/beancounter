package com.beancounter.shell.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.shell.config.ShareSightConfig;
import com.beancounter.shell.service.AssetService;
import com.beancounter.shell.service.BcService;
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
public class BcServiceTest {
  @Autowired
  private BcService bcService;

  @Autowired
  private AssetService assetService;

  @Test
  void is_PortfolioFinders() {
    Portfolio portfolioByCode = bcService.getPortfolioByCode("TEST");
    Portfolio portfolioById = bcService.getPortfolioById("TEST");

    assertThat(portfolioByCode).isEqualToComparingFieldByField(portfolioById);
  }

  @Test
  void is_PortfolioIllegalArgumentsThrowing() {
    assertThrows(BusinessException.class, () -> bcService.getPortfolioByCode(null));
    assertThrows(BusinessException.class, () -> bcService.getPortfolioByCode("IA"));
    assertThrows(BusinessException.class, () -> bcService.getPortfolioById(null));
  }

  @Test
  void is_MarketIllegalArgumentsThrowing() {
    assertThrows(BusinessException.class, () -> assetService.resolveMarket(null));
    assertThrows(BusinessException.class, () -> assetService.resolveMarket("ERR"));
  }
}
