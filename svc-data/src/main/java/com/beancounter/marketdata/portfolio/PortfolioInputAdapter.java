package com.beancounter.marketdata.portfolio;

import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.marketdata.currency.CurrencyService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PortfolioInputAdapter {

  private final CurrencyService currencyService;

  PortfolioInputAdapter(CurrencyService currencyService) {
    this.currencyService = currencyService;
  }

  public Collection<Portfolio> prepare(SystemUser owner, Collection<PortfolioInput> portfolios) {
    Collection<Portfolio> results = new ArrayList<>();
    for (PortfolioInput portfolio : portfolios) {
      results.add(prepare(owner, portfolio));
    }
    return results;
  }

  private Portfolio prepare(SystemUser owner, PortfolioInput portfolioInput) {
    log.debug("Creating for {}", owner.getId());
    return Portfolio.builder()
        .id(KeyGenUtils.format(UUID.randomUUID()))
        .code(portfolioInput.getCode().toUpperCase())
        .name(portfolioInput.getName())
        .currency(currencyService.getCode(portfolioInput.getCurrency()))
        .base(currencyService.getCode(portfolioInput.getBase()))
        .owner(owner)
        .build();
  }

  void fromInput(PortfolioInput data, Portfolio existing) {
    existing.setName(data.getName());
    existing.setCode(data.getCode().toUpperCase());
    existing.setCurrency(currencyService.getCode(data.getCurrency()));
    existing.setBase(currencyService.getCode(data.getBase()));
  }


}
