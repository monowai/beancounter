package com.beancounter.common.contracts;

import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.utils.CurrencyKeyDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class FxPairResults {
  @JsonDeserialize(keyUsing = CurrencyKeyDeserializer.class)
  private Map<IsoCurrencyPair, FxRate> rates = new HashMap<>();
}
