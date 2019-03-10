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
  TrnType trnType;
  Asset asset;
  TransactionId id;
  Portfolio portfolio;
  Market market;


  Date tradeDate;
  Date settleDate;

  BigDecimal quantity;
  BigDecimal price;
  @Builder.Default
  BigDecimal fees = BigDecimal.ZERO;
  @Builder.Default
  BigDecimal tax = BigDecimal.ZERO;
  @Builder.Default
  BigDecimal tradeAmount = BigDecimal.ZERO; // Amount spent in trade currency
  BigDecimal cashAmount;
  BigDecimal tradeRate;

  String tradeCurrency;
  String comments;

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class TransactionBuilder {

  }
}
