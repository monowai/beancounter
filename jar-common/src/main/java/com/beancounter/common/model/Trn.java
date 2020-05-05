package com.beancounter.common.model;

import com.beancounter.common.identity.CallerRef;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Representation of a Financial Transaction.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"provider", "batch", "callerId"})})
public class Trn {
  @Id
  private String id;
  @Embedded
  private CallerRef callerRef;
  private TrnType trnType;
  @ManyToOne
  private Portfolio portfolio;
  @ManyToOne
  private Asset asset;
  @ManyToOne
  private Asset cashAsset;
  @ManyToOne
  private Currency tradeCurrency;
  @ManyToOne
  private Currency cashCurrency;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  @JsonSerialize(using = LocalDateSerializer.class)
  @JsonDeserialize(using = LocalDateDeserializer.class)
  private LocalDate tradeDate;
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  @JsonSerialize(using = LocalDateSerializer.class)
  @JsonDeserialize(using = LocalDateDeserializer.class)
  private LocalDate settleDate;

  // Scale to support Mutual Fund pricing
  @Column(precision = 15, scale = 6)
  private BigDecimal quantity;
  // In trade Currency - scale is to support Mutual Fund pricing.
  @Column(precision = 15, scale = 6)
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
  // Trade amount in settlement currency.
  private BigDecimal cashAmount;
  // Trade CCY to cash settlement currency
  @Column(precision = 10, scale = 6)
  private BigDecimal tradeCashRate;
  // Trade Currency to system Base Currency
  @Column(precision = 10, scale = 6)
  private BigDecimal tradeBaseRate;
  // Trade CCY to portfolio reference  currency
  @Column(precision = 10, scale = 6)
  private BigDecimal tradePortfolioRate;

  @Builder.Default
  private String version = "1";

  private String comments;

}
