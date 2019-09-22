package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class FxPairResults {
  @JsonDeserialize(keyUsing = CurrencyKeyDeserializer.class)
  Map<CurrencyPair, FxRate> rates = new HashMap<>();
}
