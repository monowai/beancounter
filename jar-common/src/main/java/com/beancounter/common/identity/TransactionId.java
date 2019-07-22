package com.beancounter.common.identity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;

/**
 * Uniquely identifies a transaction, within a batch from a data provider.
 * @author mikeh
 * @since 2019-02-10
 */
@Data
@Builder
@JsonDeserialize(builder = TransactionId.TransactionIdBuilder.class)
public class TransactionId {
  private String provider;
  private Integer batch;
  private Integer id;

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class TransactionIdBuilder {

  }

}
