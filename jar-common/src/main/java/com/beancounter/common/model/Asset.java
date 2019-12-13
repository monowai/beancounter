package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A representation of an instrument traded on a market.
 * 
 * @author mikeh
 * @since 2019-01-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {
  private String id;
  private String code;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String name;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String category;
  @NonNull
  private Market market;

}
