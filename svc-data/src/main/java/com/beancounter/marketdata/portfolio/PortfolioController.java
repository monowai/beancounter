package com.beancounter.marketdata.portfolio;

import com.beancounter.auth.OauthRoles;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.marketdata.registration.SystemUserService;
import java.util.Collections;
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
@PreAuthorize("hasRole('" + OauthRoles.ROLE_USER + "')")
public class PortfolioController {
  private PortfolioService portfolioService;
  private SystemUserService systemUserService;

  @Autowired
  void setPortfolioService(PortfolioService portfolioService, SystemUserService systemUserService) {
    this.portfolioService = portfolioService;
    this.systemUserService = systemUserService;
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
  PortfolioRequest savePortfolio(
      final @AuthenticationPrincipal Jwt jwt, @RequestBody PortfolioRequest portfolio) {
    SystemUser owner = systemUserService.find(jwt.getSubject());
    return PortfolioRequest.builder()
        .data(portfolioService.save(owner, portfolio.getData()))
        .build();
  }
}
