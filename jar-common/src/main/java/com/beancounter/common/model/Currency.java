package com.beancounter.common.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@ToString(onlyExplicitlyIncluded = true)
public class Currency {
  @NonNull
  @Id
  @ToString.Include
  private String code;
  private String name;
  private String symbol;

}
