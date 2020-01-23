package com.beancounter.common.contracts;

import com.beancounter.common.model.Positions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PositionResponse {
  private Positions data;

}
