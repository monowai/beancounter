package com.beancounter.ingest.model;

import com.beancounter.common.model.Portfolio;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = IngestionRequest.IngestionRequestBuilder.class)
public class IngestionRequest {

  private String sheetId;
  private String filter;
  @Builder.Default
  private boolean ratesIgnored = true;
  private Portfolio portfolio;

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class IngestionRequestBuilder {
  }


}
