package com.beancounter.marketdata.portfolio;

import com.beancounter.auth.RoleHelper;
import com.beancounter.common.contracts.PortfolioResponse;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.marketdata.registration.SystemUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
  private SystemUserService systemUserService;

  @Autowired
  void setPortfolioService(PortfolioService portfolioService, SystemUserService systemUserService) {
    this.portfolioService = portfolioService;
    this.systemUserService = systemUserService;
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

  @GetMapping("/code/{code}")
  public PortfolioResponse getPortfolioByCode(@PathVariable String code) {
    Portfolio portfolio = this.portfolioService.findByCode(code);
    return PortfolioResponse.builder()
        .data(portfolio)
        .build();

  }

//  @PatchMapping("/{id}")
//  PortfolioResponse savePortfolio(
//      final @AuthenticationPrincipal Jwt jwt,
//      @PathVariable String id,
//      @RequestBody PortfolioResponse portfolio) {
//    SystemUser owner = systemUserService.find(jwt.getSubject());
//    return PortfolioResponse.builder()
//        .data(portfolioService.save(owner, portfolio.getData()))
//        .build();
//  }

  @PostMapping
  PortfoliosRequest savePortfolios(
      final @AuthenticationPrincipal Jwt jwt, @RequestBody PortfoliosRequest portfolio) {
    SystemUser owner = systemUserService.find(jwt.getSubject());
    return PortfoliosRequest.builder()
        .data(portfolioService.save(owner, portfolio.getData()))
        .build();
  }
}
