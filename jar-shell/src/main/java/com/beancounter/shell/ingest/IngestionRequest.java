package com.beancounter.shell.ingest;

import com.beancounter.common.model.Portfolio;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IngestionRequest {

  @Builder.Default
  private String type = "CSV";
  private String file;
  private String filter;
  private String provider;
  @Builder.Default
  private boolean ratesIgnored = true;
  @Builder.Default
  private boolean trnPersist = true;
  private String portfolioCode;
  // Portfolio resolved from portfolioCode
  private Portfolio portfolio;

}
