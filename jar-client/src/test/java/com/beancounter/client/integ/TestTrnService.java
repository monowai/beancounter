package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.config.ClientConfig;
import com.beancounter.client.services.TrnService;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.TrustedTrnQuery;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.PortfolioUtils;
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
public class TestTrnService {

  private static final Portfolio portfolio;

  static {
    portfolio = PortfolioUtils.getPortfolio(
        "TEST", "NZD Portfolio", new Currency("NZD")
    );
  }

  @Autowired
  private TrnService trnService;

  @Test
  void is_TrnsReturnedForPortfolioId() {
    TrnResponse trnResponse = trnService.query(portfolio);
    assertThat(trnResponse).isNotNull().hasFieldOrProperty("data");
    assertThat(trnResponse.getData()).isNotEmpty();// Don't care about the contents here.
  }

  @Test
  void is_TrnsReturnedForPortfolioAssetId() {
    TrustedTrnQuery query = new TrustedTrnQuery(
        portfolio, new DateUtils().getDate("2020-05-01"), "KMI");
    TrnResponse queryResults = trnService.query(query);
    assertThat(queryResults).isNotNull().hasFieldOrProperty("data");
    assertThat(queryResults.getData()).isNotEmpty();// Don't care about the contents here.
  }


}
