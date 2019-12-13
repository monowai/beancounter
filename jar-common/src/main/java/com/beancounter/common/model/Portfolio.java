package com.beancounter.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Owner of a collection of Positions.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class Portfolio {
  private String id;
  private String code;
  private String name;

  private Currency currency;
  @Builder.Default
  private Currency base = Currency.builder().code("USD").build();


}
