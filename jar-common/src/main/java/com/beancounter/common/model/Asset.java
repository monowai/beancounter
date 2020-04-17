package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A representation of an instrument traded on a market.
 *
 * @author mikeh
 * @since 2019-01-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class Asset {
  @Id
  private String id;
  private String code;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String name;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Builder.Default
  private String category = "Common Stock";
  // Market is managed as static data; the code alone is persisted.
  @Transient
  private Market market;
  @JsonIgnore
  private String marketCode;
}
