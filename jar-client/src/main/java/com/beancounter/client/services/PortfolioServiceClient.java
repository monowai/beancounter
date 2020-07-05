package com.beancounter.client.services;

import com.beancounter.auth.common.TokenService;
import com.beancounter.common.contracts.PortfolioResponse;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.DateUtils;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
public class PortfolioServiceClient {
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(PortfolioServiceClient.class);
  private final PortfolioGw portfolioGw;
  private final TokenService tokenService;
  private final DateUtils dateUtils = new DateUtils();

  public PortfolioServiceClient(PortfolioGw portfolioGw, TokenService tokenService) {
    this.portfolioGw = portfolioGw;
    this.tokenService = tokenService;
  }

  public Portfolio getPortfolioByCode(String portfolioCode) {
    PortfolioResponse response = null;
    if (portfolioCode != null) {
      response = portfolioGw.getPortfolioByCode(tokenService.getBearerToken(), portfolioCode);
    }
    return getOrThrow(portfolioCode, response);
  }

  public Portfolio getPortfolioById(String portfolioId) {
    PortfolioResponse response = null;
    if (portfolioId != null) {
      response = portfolioGw.getPortfolioById(tokenService.getBearerToken(), portfolioId);
    }
    return getOrThrow(portfolioId, response);
  }

  public PortfoliosResponse getPortfolios() {
    return portfolioGw.getPortfolios(tokenService.getBearerToken());
  }


  public PortfoliosResponse add(PortfoliosRequest portfoliosRequest) {
    return portfolioGw.addPortfolios(tokenService.getBearerToken(), portfoliosRequest);
  }

  public PortfoliosResponse getWhereHeld(String assetId, LocalDate tradeDate) {
    return portfolioGw.getWhereHeld(
        tokenService.getBearerToken(),
        assetId,
        dateUtils.getDateString(tradeDate));
  }

  private Portfolio getOrThrow(String portfolioCode, PortfolioResponse response) {
    if (response == null || response.getData() == null) {
      throw new BusinessException(String.format("Unable to find portfolio %s", portfolioCode));
    }
    return response.getData();
  }

  @FeignClient(name = "portfolios",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface PortfolioGw {
    @GetMapping(value = "/portfolios",
        produces = {MediaType.APPLICATION_JSON_VALUE})
    PortfoliosResponse getPortfolios(
        @RequestHeader("Authorization") String bearerToken);

    @GetMapping(value = "/portfolios/{id}",
        produces = {MediaType.APPLICATION_JSON_VALUE})
    PortfolioResponse getPortfolioById(
        @RequestHeader("Authorization") String bearerToken,
        @PathVariable("id") String id);

    @GetMapping(value = "/portfolios/code/{code}",
        produces = {MediaType.APPLICATION_JSON_VALUE})
    PortfolioResponse getPortfolioByCode(
        @RequestHeader("Authorization") String bearerToken,
        @PathVariable("code") String code);

    @GetMapping(value = "/portfolios/asset/{assetId}/{tradeDate}",
        produces = {MediaType.APPLICATION_JSON_VALUE})
    PortfoliosResponse getWhereHeld(
        @RequestHeader("Authorization") String bearerToken,
        @PathVariable("assetId") String assetId,
        @PathVariable("tradeDate") String tradeDate
    );

    @PostMapping(value = "/portfolios",
        produces = {MediaType.APPLICATION_JSON_VALUE})
    PortfoliosResponse addPortfolios(
        @RequestHeader("Authorization") String bearerToken,
        PortfoliosRequest portfoliosRequest);

  }
}
