package com.beancounter.position.model;

import com.beancounter.common.model.Currency;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FxReport {
  private Currency base;
  private Currency portfolio;
}
