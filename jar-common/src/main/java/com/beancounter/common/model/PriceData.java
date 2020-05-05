package com.beancounter.common.model;

import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.MathUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PriceData {
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DateUtils.format)
  @JsonSerialize(using = LocalDateSerializer.class)
  @JsonDeserialize(using = LocalDateDeserializer.class)
  private LocalDate priceDate;

  private BigDecimal open;
  @Builder.Default
  private BigDecimal close = BigDecimal.ZERO;
  private BigDecimal low;
  private BigDecimal high;
  private BigDecimal previousClose;
  private BigDecimal change;
  private BigDecimal changePercent;
  private Integer volume;

  public static PriceData of(MarketData marketData) {
    return of(marketData, BigDecimal.ONE);
  }

  public static PriceData of(MarketData mktData, BigDecimal rate) {
    PriceData result = PriceData.builder()
        .priceDate(mktData.getPriceDate())
        .open(MathUtils.multiply(mktData.getOpen(), rate))
        .close(MathUtils.multiply(mktData.getClose(), rate))
        .previousClose(MathUtils.multiply(mktData.getPreviousClose(), rate))
        .change(MathUtils.multiply(mktData.getChange(), rate))
        .changePercent(mktData.getChangePercent())
        .volume(mktData.getVolume())  // Approximation based on current rate.
        .build();

    if (MathUtils.hasValidRate(rate) && result.previousClose != null && result.close != null) {
      // Convert
      BigDecimal change = new BigDecimal("1.00")
          .subtract(MathUtils.changePercent(result.previousClose, result.close, 4));

      result.setChangePercent(change);
      result.setChange(result.close.subtract(result.previousClose));
    }
    return result;
  }

}
