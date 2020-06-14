package com.beancounter.marketdata.portfolio;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.common.contracts.PortfolioResponse;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.DateUtils;
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
@PreAuthorize("hasAnyRole('" + RoleHelper.OAUTH_USER + "', '" + RoleHelper.OAUTH_M2M + "')")
public class
PortfolioController {
  private final DateUtils dateUtils = new DateUtils();
  private PortfolioService portfolioService;

  @Autowired
  void setPortfolioService(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
  }

  @GetMapping
  public PortfoliosResponse getPortfolios() {
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

  @GetMapping(value = "/asset/{assetId}/{tradeDate}")
  PortfoliosResponse getWhereHeld(
      @PathVariable("assetId") String assetId,
      @PathVariable("tradeDate") String tradeDate) {

    return portfolioService.findWhereHeld(assetId, dateUtils.getDate(tradeDate));

  }

}
