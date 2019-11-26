package com.beancounter.common.json;

import com.beancounter.common.model.CurrencyPair;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

public class CurrencyKeyDeserializer extends KeyDeserializer {
  @Override
  public CurrencyPair deserializeKey(String key, DeserializationContext ctxt) {
    return CurrencyPair.builder()
        .from(key.substring(0, 3))
        .to(key.substring(4, 7)).build();
  }
}
