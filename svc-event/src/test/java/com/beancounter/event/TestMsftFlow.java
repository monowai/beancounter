package com.beancounter.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.beancounter.auth.common.TokenService;
import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.input.TrustedTrnQuery;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.event.integration.PositionGateway;
import com.beancounter.event.service.EventService;
import com.beancounter.event.service.PositionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = EventBoot.class)
@ActiveProfiles("test") // Ignore Kafka
public class TestMsftFlow {
  @Autowired
  private EventService eventService;

  @Autowired
  private PositionService positionService;

  @Test
  void is_DividendRequestIgnore() throws Exception {
    ObjectMapper om = new ObjectMapper();

    TrustedEventInput trustedEvent = om.readValue(
        new ClassPathResource("/msft-flow/1-event.json").getFile(),
        TrustedEventInput.class);
    PortfoliosResponse whereHeld = om.readValue(
        new ClassPathResource("/msft-flow/2-where-held.json").getFile(), PortfoliosResponse.class);
    PositionResponse positionResponse = om.readValue(
        new ClassPathResource("/msft-flow/3-position.json").getFile(), PositionResponse.class);

    PositionGateway positionGateway = Mockito.mock(PositionGateway.class);
    CorporateEvent dividend = trustedEvent.getData();
    DateUtils dateUtils = new DateUtils();

    when(
        positionGateway.get("demo",
            dividend.getAssetId(),
            dateUtils.getDateString(dividend.getRecordDate()))
    ).thenReturn(positionResponse);

    Portfolio portfolio = whereHeld.getData().iterator().next();

    when(
        positionGateway
            .query(
                "demo",
                TrustedTrnQuery.builder()
                    .portfolio(portfolio)
                    .tradeDate(dividend.getRecordDate())
                    .assetId(dividend.getAssetId())
                    .build())
    ).thenReturn(positionResponse);
    PortfolioServiceClient.PortfolioGw portfolioGw = mock(PortfolioServiceClient.PortfolioGw.class);
    when(portfolioGw.getWhereHeld(
        "demo",
        dividend.getAssetId(),
        dateUtils.getDateString(dividend.getRecordDate())))
        .thenReturn(whereHeld);

    TokenService tokenService = mock(TokenService.class);
    when(tokenService.getBearerToken()).thenReturn("demo");
    PortfolioServiceClient portfolioServiceClient =
        new PortfolioServiceClient(portfolioGw, tokenService);

    positionService.setPositionGateway(positionGateway);
    positionService.setTokenService(tokenService);
    positionService.setPortfolioClientService(portfolioServiceClient);

    Collection<TrustedTrnEvent> results = eventService.processMessage(trustedEvent);
    assertThat(results).isEmpty();

  }
}
