package com.beancounter.common.contracts;

import com.beancounter.common.model.Market;
import java.util.ArrayList;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketResponse {
  @Builder.Default
  private Collection<Market> data = new ArrayList<>();
}
