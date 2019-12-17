package com.beancounter.marketdata.portfolio;

import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.model.Portfolio;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  @GetMapping("/{id}")
  public PortfolioRequest getPortfolio(@PathVariable String id) {
    Portfolio portfolio = this.portfolioService.find(id);
    return PortfolioRequest.builder()
        .data(Collections.singletonList(portfolio))
        .build();

  }

  @GetMapping("/{code}/code")
  public PortfolioRequest getPortfolioByCode(@PathVariable String code) {
    Portfolio portfolio = this.portfolioService.findByCode(code.toUpperCase());
    return PortfolioRequest.builder()
        .data(Collections.singletonList(portfolio))
        .build();

  }

  @PostMapping
  PortfolioRequest savePortfolio(@RequestBody PortfolioRequest portfolio) {
    return PortfolioRequest.builder()
        .data(portfolioService.save(portfolio.getData()))
        .build();
  }
}
