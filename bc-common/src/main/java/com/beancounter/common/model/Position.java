package com.beancounter.common.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

/**
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
public class Position {
    Asset asset;
    Portfolio portfolio;
    @Builder.Default @Getter
    MoneyValues moneyValues = MoneyValues.builder().build();
    @Builder.Default @Getter
    Quantity quantity = Quantity.builder().build();

}
