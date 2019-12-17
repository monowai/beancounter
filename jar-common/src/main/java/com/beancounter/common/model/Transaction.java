package com.beancounter.common.model;

import com.beancounter.common.identity.TransactionId;
import java.math.BigDecimal;
import java.util.Date;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
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
@NoArgsConstructor
@AllArgsConstructor
@ToString(of = {"asset", "trnType", "id"})
public class Transaction {
  private TransactionId id;
  private TrnType trnType;
  @Transient
  private Portfolio portfolio;
  private String portfolioId;
  @NonNull
  private Asset asset;
  private Asset cashAsset;
  private Currency tradeCurrency;
  private Currency cashCurrency;

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

  // Trade CCY to cash settlement currency
  private BigDecimal tradeCashRate;
  // Trade Currency to system Base Currency
  private BigDecimal tradeBaseRate;
  // Trade CCY to portfolio reference  currency
  private BigDecimal tradePortfolioRate;

  private String comments;

}
