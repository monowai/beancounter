package com.beancounter.common.input;

import com.beancounter.common.identity.CallerRef;
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
  private CallerRef callerRef;
  private List<String> row;
}
