package com.beancounter.common.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
public class Quantity {
    @Builder.Default
    BigDecimal sold = new BigDecimal(0d);
    @Builder.Default
    BigDecimal purchased = new BigDecimal(0d);

    public BigDecimal getTotal() {
        return purchased.add(sold);
    }
}
