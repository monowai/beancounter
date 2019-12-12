package com.beancounter.position.portfolio;

import com.beancounter.common.contracts.PortfolioResults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
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
  PortfolioResults getPortfolios() {
    return PortfolioResults.builder()
        .data(portfolioService.getPortfolios())
        .build();
  }
}
