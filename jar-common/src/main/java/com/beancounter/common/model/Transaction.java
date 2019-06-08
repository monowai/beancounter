package com.beancounter.common.model;

import com.beancounter.common.identity.TransactionId;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;


/**
 * Representation of a Financial Transaction.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Data
@Builder
@ToString(of = {"asset","trnType","id"})
@JsonDeserialize(builder = Transaction.TransactionBuilder.class)
public class Transaction {
  private TrnType trnType;
  private Asset asset;
  private TransactionId id;
  private Portfolio portfolio;
  private Market market;

  private Date tradeDate;
  private Date settleDate;

  private BigDecimal quantity;
  private BigDecimal price;
  @Builder.Default
  private BigDecimal fees = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal tax = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal tradeAmount = BigDecimal.ZERO; // Amount spent in trade currency
  private BigDecimal cashAmount;
  private BigDecimal tradeRate;

  private String tradeCurrency;
  private String comments;

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class TransactionBuilder {

  }
}
