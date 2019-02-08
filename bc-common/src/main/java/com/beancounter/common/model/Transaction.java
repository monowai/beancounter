package com.beancounter.common.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author mikeh
 * @since 2019-02-07
 */
@Data
@Builder
public class Transaction {
    Portfolio portfolio;
    Asset asset;
    Market market;
    
    TrnType trnType;

    Date tradeDate;
    Date settleDate;

    BigDecimal quantity;
    BigDecimal price;
    BigDecimal fees;
    BigDecimal tradeAmount;
    BigDecimal cashAmount;
    BigDecimal tradeRate; 

    String tradeCurrency;
    String comments;

}
