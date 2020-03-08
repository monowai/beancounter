package com.beancounter.common.contracts;

import com.beancounter.common.model.Portfolio;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PortfolioResponse implements Payload<Portfolio> {

  private Portfolio data;
}
