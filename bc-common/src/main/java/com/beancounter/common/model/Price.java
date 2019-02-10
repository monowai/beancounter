package com.beancounter.common.model;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Builder;
import lombok.Getter;


@Getter
@Builder
public class Price {
  private Asset asset;
  private Date date;
  private BigDecimal price;

}
