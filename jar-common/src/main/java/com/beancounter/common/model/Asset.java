package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"code", "marketCode"})})
@ToString(onlyExplicitlyIncluded = true)
public class Asset {
  @Id
  private String id;
  @ToString.Include
  private String code;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @ToString.Include
  private String name;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Builder.Default
  private String category = "Common Stock";
  // Market is managed externally as static data; the code alone is persisted.
  @Transient
  @ToString.Include
  private Market market;
  // Caller doesn't see marketCode
  @JsonIgnore
  private String marketCode;

  @Transient
  @JsonIgnore
  // Is this asset stored locally?
  public Boolean isKnown() {
    return id != null && ! code.equalsIgnoreCase(id);
  }
}
