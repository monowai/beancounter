package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.client.services.ClientConfig;
import com.beancounter.client.services.PortfolioService;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.model.Portfolio;
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
  void is_AddFailing() {
    PortfoliosRequest request = PortfoliosRequest.builder()
        .data(Collections.singleton(PortfolioInput.builder().code("ABC").build()))
        .build();
    // Null returned for an Add request
    assertThat(portfolioService.add(request)).isNull();
  }

  @Test
  void is_PortfolioAddRequest() {
    PortfoliosRequest request = PortfoliosRequest.builder()
        .data(Collections.singleton(PortfolioInput.builder()
            .code("SGD")
            .name("SGD Balanced")
            .currency("SGD")
            .base("USD")
            .build()))
        .build();
    // Null returned for an Add request
    PortfoliosResponse response = portfolioService.add(request);
    assertThat(response).isNotNull().hasNoNullFieldsOrProperties();
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
