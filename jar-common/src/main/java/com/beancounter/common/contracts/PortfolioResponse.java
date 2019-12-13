package com.beancounter.common.contracts;

import com.beancounter.common.model.Portfolio;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PortfolioResponse {
  private Collection<Portfolio> data;
}
