package com.beancounter.common.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;


/**
 * Various data points representing marketdata for an asset.
 *
 * @author mikeh
 * @since 2019-01-27
 */
@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class MarketData {
  private Asset asset;
  private String date;
  private BigDecimal open;
  @Builder.Default
  private BigDecimal close = BigDecimal.ZERO;
  private BigDecimal low;
  private BigDecimal high;
  private BigDecimal volume;

}
