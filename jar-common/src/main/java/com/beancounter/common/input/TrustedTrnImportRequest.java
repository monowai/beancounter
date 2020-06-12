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
public class TrustedTrnImportRequest {
  private Portfolio portfolio;
  private CallerRef callerRef;
  private String message;
  private List<String> row;
}
