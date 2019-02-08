package com.beancounter.common.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
public class MoneyValues {

    @Builder.Default
    BigDecimal dividends = new BigDecimal(0d);
    @Builder.Default
    BigDecimal marketCost = new BigDecimal(0d); // Cost in Local Market terms
    @Builder.Default
    BigDecimal fees = new BigDecimal(0d);
}
