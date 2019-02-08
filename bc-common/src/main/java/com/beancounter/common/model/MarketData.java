package com.beancounter.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author mikeh
 * @since 2019-01-27
 */
@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class MarketData {
    String assetId;
    Date date;
    BigDecimal open;
    BigDecimal close;
    BigDecimal low;
    BigDecimal high;
    BigDecimal volume;
}
