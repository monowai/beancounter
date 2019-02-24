package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;

/**
 * A representation of an instrument traded on a market.
 * 
 * @author mikeh
 * @since 2019-01-27
 */
@Data(staticConstructor = "of")
@Builder
@JsonDeserialize(builder = Asset.AssetBuilder.class)
public class Asset {
  String code;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String name;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String category;
  Market market;

  @JsonPOJOBuilder(withPrefix = "")
  public static class AssetBuilder {
  }
}
