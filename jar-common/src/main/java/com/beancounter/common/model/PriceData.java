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
import lombok.Builder;
import lombok.Data;

@Data
@Builder
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

  public static PriceData of(MarketData mktData, BigDecimal rate) {
    PriceData result = PriceData.builder()
        .change(MathUtils.divide(mktData.getChange(), rate))
        .changePercent(mktData.getChangePercent())
        .close(MathUtils.divide(mktData.getClose(), rate))
        .open(mktData.getOpen())
        .priceDate(mktData.getPriceDate())
        // Approximation based on current rate.
        .previousClose(MathUtils.divide(mktData.getPreviousClose(), rate))
        .volume(mktData.getVolume())
        .build();

    if (rate.compareTo(BigDecimal.ONE) != 0 &&
        result.previousClose != null &&
        result.close != null) {
      BigDecimal change = new BigDecimal("1.00").subtract(
          MathUtils.changePercent(result.previousClose, result.close, 4));

      result.setChangePercent(change);
    }
    return result;
  }

}
