package com.beancounter.common.model;

import com.beancounter.common.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


/**
 * Various data points representing marketdata for an asset.
 *
 * @author mikeh
 * @since 2019-01-27
 */
@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "priceDate"}))
public class MarketData {
  @Getter
  @Id
  @JsonIgnore
  private String id;
  @ManyToOne
  private Asset asset;
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DateUtils.format)
  @JsonSerialize(using = LocalDateSerializer.class)
  @JsonDeserialize(using = LocalDateDeserializer.class)
  private LocalDate priceDate;

  @Column(precision = 15, scale = 6)
  private BigDecimal open;
  @Builder.Default
  @Column(precision = 15, scale = 6)
  private BigDecimal close = BigDecimal.ZERO;
  @Column(precision = 15, scale = 6)
  private BigDecimal low;
  @Column(precision = 15, scale = 6)
  private BigDecimal high;
  @Column(precision = 15, scale = 6)
  private BigDecimal previousClose;
  @Column(precision = 7, scale = 4)
  private BigDecimal change;
  @Column(precision = 7, scale = 4)
  private BigDecimal changePercent;
  private Integer volume;

}
