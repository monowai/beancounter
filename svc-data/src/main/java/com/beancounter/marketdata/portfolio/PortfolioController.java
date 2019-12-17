package com.beancounter.marketdata.portfolio;

import com.beancounter.common.contracts.PortfolioRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolios")
public class PortfolioController {
  private PortfolioService portfolioService;

  @Autowired
  void setPortfolioService(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
  }

  @GetMapping
  PortfolioRequest getPortfolios() {
    return PortfolioRequest.builder()
        .data(portfolioService.getPortfolios())
        .build();
  }

  @PostMapping
  PortfolioRequest savePortfolio(@RequestBody PortfolioRequest portfolio) {
    return PortfolioRequest.builder()
        .data(portfolioService.save(portfolio.getData()))
        .build();
  }
}
