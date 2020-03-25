package com.beancounter.common.input;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustedTrnRequest {
  private Portfolio portfolio;
  private Asset asset;
  private String provider;
  private List<String> row;
}
