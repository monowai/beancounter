package com.beancounter.client;

import com.beancounter.auth.TokenService;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Slf4j
@Service
public class PortfolioService {
  private PortfolioGw portfolioGw;
  private TokenService tokenService;

  PortfolioService(PortfolioGw portfolioGw, TokenService tokenService) {
    this.portfolioGw = portfolioGw;
    this.tokenService = tokenService;
  }

  public Portfolio getPortfolioByCode(String portfolioCode) {
    PortfolioRequest response = null;
    if (portfolioCode != null) {
      response = portfolioGw.getPortfolioByCode(tokenService.getBearerToken(), portfolioCode);
    }
    return getOrThrow(portfolioCode, response);
  }

  public Portfolio getPortfolioById(String portfolioId) {
    PortfolioRequest response = null;
    if (portfolioId != null) {
      response = portfolioGw.getPortfolioById(tokenService.getBearerToken(), portfolioId);
    }
    return getOrThrow(portfolioId, response);
  }

  public PortfolioRequest add(PortfolioRequest portfolioRequest) {
    return portfolioGw.addPortfolios(tokenService.getBearerToken(), portfolioRequest);
  }

  private Portfolio getOrThrow(String portfolioCode, PortfolioRequest response) {
    if (response == null || response.getData().isEmpty()) {
      throw new BusinessException(String.format("Unable to find portfolio %s", portfolioCode));
    }
    return response.getData().iterator().next();
  }

  @FeignClient(name = "portfolios",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface PortfolioGw {
    @GetMapping(value = "/portfolios/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    PortfolioRequest getPortfolioById(
        @RequestHeader("Authorization") String bearerToken,
        @PathVariable("id") String id);

    @GetMapping(value = "/portfolios/{code}/code", produces = {MediaType.APPLICATION_JSON_VALUE})
    PortfolioRequest getPortfolioByCode(
        @RequestHeader("Authorization") String bearerToken,
        @PathVariable("code") String code);

    @PostMapping(value = "/portfolios", produces = {MediaType.APPLICATION_JSON_VALUE})
    PortfolioRequest addPortfolios(
        @RequestHeader("Authorization") String bearerToken,
        PortfolioRequest portfolioRequest);

  }
}
