package com.beancounter.ingest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IngestionRequest {

  private String sheetId;
  private String filter;
  @Builder.Default
  private boolean ratesIgnored = true;
  @Builder.Default
  private boolean trnPersist = true;
  private String portfolioCode;

}
