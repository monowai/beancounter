package com.beancounter.marketdata.controller;

import com.beancounter.auth.RoleHelper;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.TrnId;
import com.beancounter.common.model.Portfolio;
import com.beancounter.marketdata.portfolio.PortfolioService;
import com.beancounter.marketdata.trn.TrnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trns")
@CrossOrigin
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
public class TrnController {

  private TrnService trnService;
  private PortfolioService portfolioService;

  @Autowired
  void setServices(TrnService trnService,
                   PortfolioService portfolioService) {
    this.trnService = trnService;
    this.portfolioService = portfolioService;
  }

  @GetMapping(value = "/{portfolioId}", produces = MediaType.APPLICATION_JSON_VALUE)
  TrnResponse find(@PathVariable("portfolioId") String portfolioId) {
    Portfolio portfolio = portfolioService.find(portfolioId);

    return trnService.find(portfolio);
  }

  @GetMapping(value = "/{provider}/{batch}/{id}")
  TrnResponse find(
      @PathVariable("provider") String provider,
      @PathVariable("batch") String batch,
      @PathVariable("id") String id
  ) {
    return trnService.find(TrnId.builder()
        .provider(provider)
        .batch(batch)
        .id(id)
        .build());
  }

  @PostMapping(
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  TrnResponse update(@RequestBody TrnRequest trnRequest) {
    Portfolio portfolio = portfolioService.find(trnRequest.getPortfolioId());
    return trnService.save(portfolio, trnRequest);
  }

  @DeleteMapping(value = "/{portfolioId}")
  Long purge(@PathVariable("portfolioId") String portfolioId) {
    Portfolio portfolio = portfolioService.find(portfolioId);
    return trnService.purge(portfolio);
  }

  @GetMapping(value = "/{portfolioId}/{assetId}", produces = MediaType.APPLICATION_JSON_VALUE)
  TrnResponse findByAsset(
      @PathVariable("portfolioId") String portfolioId,
      @PathVariable("assetId") String assetId
  ) {
    return trnService.find(portfolioService.find(portfolioId), assetId);
  }
}
