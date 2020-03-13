package com.beancounter.marketdata.portfolio;

import com.beancounter.auth.RoleHelper;
import com.beancounter.common.contracts.PortfolioInput;
import com.beancounter.common.contracts.PortfolioResponse;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.model.Portfolio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolios")
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
public class PortfolioController {
  private PortfolioService portfolioService;

  @Autowired
  void setPortfolioService(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
  }

  @GetMapping
  PortfoliosResponse getPortfolios() {
    return PortfoliosResponse.builder()
        .data(portfolioService.getPortfolios())
        .build();
  }

  @GetMapping("/{id}")
  public PortfolioResponse getPortfolio(@PathVariable String id) {
    Portfolio portfolio = this.portfolioService.find(id);
    return PortfolioResponse.builder()
        .data(portfolio)
        .build();
  }

  @DeleteMapping("/{id}")
  public String deletePortfolio(@PathVariable String id) {
    this.portfolioService.delete(id);
    return "ok";
  }

  @GetMapping("/code/{code}")
  public PortfolioResponse getPortfolioByCode(@PathVariable String code) {
    Portfolio portfolio = this.portfolioService.findByCode(code);
    return PortfolioResponse.builder()
        .data(portfolio)
        .build();

  }

  @PatchMapping(value = "/{id}")
  PortfolioResponse savePortfolio(
      @PathVariable String id,
      @RequestBody PortfolioInput portfolio) {
    return PortfolioResponse.builder()
        .data(portfolioService.update(id, portfolio))
        .build();
  }

  @PostMapping
  PortfoliosResponse savePortfolios(
      @RequestBody PortfoliosRequest portfolio) {
    return PortfoliosResponse.builder()
        .data(portfolioService.save(portfolio.getData()))
        .build();
  }
}
