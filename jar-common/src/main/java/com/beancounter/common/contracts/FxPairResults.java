package com.beancounter.common.contracts;

import com.beancounter.common.json.CurrencyKeyDeserializer;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class FxPairResults {
  @JsonDeserialize(keyUsing = CurrencyKeyDeserializer.class)
  private Map<CurrencyPair, FxRate> rates = new HashMap<>();
}
