package com.beancounter.common.contracts;

import com.beancounter.common.model.Trn;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionRequest {
  private String portfolioId;
  private Collection<Trn> trns;
}