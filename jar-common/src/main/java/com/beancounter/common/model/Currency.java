package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@ToString(exclude = {"name"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
public class Currency {
  @NonNull
  @Id
  private String code;
  private String name;
  private String symbol;

}
