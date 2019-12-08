package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"id", "name", "symbol"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Currency {
  private String id;
  @NonNull
  private String code;
  private String name;
  private String symbol;

}
