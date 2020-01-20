package com.beancounter.common.contracts;


import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrnResponse {
  @Builder.Default
  private Collection<Transaction> transactions = new ArrayList<>();
  @Builder.Default
  private Collection<Portfolio> portfolios = new ArrayList<>();

  @JsonIgnore
  public void addPortfolio(Portfolio portfolio) {
    portfolios.add(portfolio);
  }
}
