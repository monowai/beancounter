package com.beancounter.common.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author mikeh
 * @since 2019-02-08
 */
@Getter
@Builder
public class Price {
    private Asset asset;
    private Date date;
    private BigDecimal price ;

}
