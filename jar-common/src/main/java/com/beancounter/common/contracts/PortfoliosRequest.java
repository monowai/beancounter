package com.beancounter.common.contracts;

import com.beancounter.common.input.PortfolioInput;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PortfoliosRequest {
  private Collection<PortfolioInput> data;
}
