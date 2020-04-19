package com.beancounter.marketdata.assets.figi;

import java.util.LinkedHashMap;
import lombok.Data;

@Data
public class FigiResult {
  private LinkedHashMap<String, FigiResponse> results;
}
