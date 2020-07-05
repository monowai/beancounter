package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.client.config.ClientConfig;
import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.DateUtils;
import java.util.Collections;
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
public class TestPortfolioServiceClient {
  @Autowired
  private PortfolioServiceClient portfolioService;

  @Test
  void is_PortfolioFinders() {
    Portfolio portfolioByCode = portfolioService.getPortfolioByCode("TEST");
    Portfolio portfolioById = portfolioService.getPortfolioById("TEST");

    assertThat(portfolioByCode).isEqualToComparingFieldByField(portfolioById);
  }

  @Test
  void is_AddFailing() {
    PortfoliosRequest request = new PortfoliosRequest(
        (Collections.singleton(
            new PortfolioInput("ABC", "name", "NZD", "USD"))));
    // Null returned for an Add request
    assertThat(portfolioService.add(request)).isNull();
  }

  @Test
  void is_PortfolioAddRequest() {
    PortfoliosRequest request = new PortfoliosRequest(
        Collections.singleton(
            new PortfolioInput("SGD", "SGD Balanced", "SGD", "USD")));
    // Null returned for an Add request
    PortfoliosResponse response = portfolioService.add(request);
    assertThat(response).isNotNull().hasNoNullFieldsOrProperties();
  }

  @Test
  void is_MyPortfolios() {
    PortfoliosResponse myPortfolios = portfolioService.getPortfolios();
    assertThat(myPortfolios.getData()).isNotNull().isNotEmpty();
  }

  @Test
  void is_WhereHeld() {
    PortfoliosResponse whereHeld = portfolioService.getWhereHeld(
        "KMI",
        new DateUtils().getDate("2020-05-01"));

    assertThat(whereHeld.getData()).isNotNull().isNotEmpty();

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
