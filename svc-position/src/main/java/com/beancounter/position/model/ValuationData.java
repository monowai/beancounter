package com.beancounter.position.model;

import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PriceResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValuationData {
  PriceResponse priceResponse;
  FxResponse fxResponse;
}
