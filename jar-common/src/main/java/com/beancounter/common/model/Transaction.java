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
@SuppressWarnings("UnusedAssignment")
@Data
@Builder
@ToString(of = {"asset", "trnType", "id"})
@JsonDeserialize(builder = Transaction.TransactionBuilder.class)
public class Transaction {
  private TrnType trnType;
  private Asset asset;
  private Asset cashAsset;
  private TransactionId id;
  private Market market;
  private Portfolio portfolio;
  private Currency tradeCurrency;
  private Currency cashCurrency;
  private Currency baseCurrency;

  private Date tradeDate;
  private Date settleDate;

  private BigDecimal quantity;
  // In trade Currency
  private BigDecimal price;
  @Builder.Default
  // In trade Currency
  private BigDecimal fees = BigDecimal.ZERO;
  @Builder.Default
  // In trade Currency
  private BigDecimal tax = BigDecimal.ZERO;
  @Builder.Default
  // In trade Currency
  private BigDecimal tradeAmount = BigDecimal.ZERO;
  private BigDecimal cashAmount;
  // Trade CCY to settlement  currency
  private BigDecimal cashRate;

  // Trade CCY to portfolio reference currency
  private BigDecimal tradeRate;
  @Builder.Default
  // Trade Currency to system Base Currency
  private BigDecimal baseRate = BigDecimal.ONE;

  private String comments;

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class TransactionBuilder {

  }
}
