package com.beancounter.marketdata.providers;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(exclude = "date")
public class BatchConfig {
  private String date;
  private Integer batch;
}
