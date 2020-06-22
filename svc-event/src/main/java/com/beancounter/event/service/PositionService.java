package com.beancounter.event.service;

import com.beancounter.auth.common.TokenService;
import com.beancounter.client.AssetService;
import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.input.TrustedTrnQuery;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.event.integration.PositionGateway;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PositionService {
  private final EventBehaviourFactory behaviourFactory;
  private final DateUtils dateUtils = new DateUtils();
  private AssetService assetService;
  private PositionGateway positionGateway;
  private PortfolioServiceClient portfolioService;
  private TokenService tokenService;
  @Value("${position.url:http://localhost:9500/api}")
  private String positionUrl;

  public PositionService(EventBehaviourFactory eventBehaviourFactory) {
    this.behaviourFactory = eventBehaviourFactory;
  }

  @Autowired
  public void setAssetService(AssetService assetService) {
    this.assetService = assetService;
  }

  @Autowired
  public void setTokenService(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Autowired
  public void setPositionGateway(PositionGateway positionGateway) {
    this.positionGateway = positionGateway;
  }

  @Autowired
  public void setPortfolioClientService(PortfolioServiceClient portfolioServiceClient) {
    this.portfolioService = portfolioServiceClient;
  }


  @PostConstruct
  void logConfig() {
    log.info("position.url: {}", positionUrl);
  }

  public PortfoliosResponse findWhereHeld(String assetId, LocalDate date) {
    return portfolioService.getWhereHeld(assetId, date);
  }

  public TrustedTrnEvent process(Portfolio portfolio, CorporateEvent event) {
    PositionResponse positionResponse = positionGateway
        .query(
            tokenService.getBearerToken(),
            TrustedTrnQuery.builder()
                .portfolio(portfolio)
                .tradeDate(event.getRecordDate())
                .assetId(event.getAssetId())
                .build());
    if (positionResponse.getData() != null && positionResponse.getData().hasPositions()) {
      Position position = positionResponse.getData().getPositions().values().iterator().next();
      if (position.getQuantityValues().getTotal().compareTo(BigDecimal.ZERO) != 0) {
        Event behaviour = behaviourFactory.getAdapter(event);
        assert (behaviour != null);
        return behaviour
            .calculate(positionResponse.getData().getPortfolio(), position, event);
      }
    }
    return null;
  }

  public void backFillEvents(String code, String date) {
    Portfolio portfolio = portfolioService.getPortfolioByCode(code);
    String asAt;
    if (date == null || date.equalsIgnoreCase("today")) {
      asAt = dateUtils.today();
    } else {
      asAt = dateUtils.getDateString(dateUtils.getDate(date));
    }

    PositionResponse results = positionGateway
        .get(
            tokenService.getBearerToken(),
            portfolio.getCode(),
            asAt);

    for (String key : results.getData().getPositions().keySet()) {
      Position position = results.getData().getPositions().get(key);
      if (position.getQuantityValues().getTotal().compareTo(BigDecimal.ZERO) != 0) {
        assetService.backFillEvents(position.getAsset().getId());
      }
    }
  }

}
