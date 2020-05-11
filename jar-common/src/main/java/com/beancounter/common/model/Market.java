package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

/**
 * A stock exchange.
 *
 * @author mikeh
 * @since 2019-01-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Market {
  // ToDo: Separate input props from impl to make it clearer.
  @NonNull
  @ToString.Include
  private String code;
  @Transient
  private Currency currency;
  @JsonIgnore
  private String currencyId;
  @JsonIgnore
  private String timezoneId;
  @Builder.Default
  private TimeZone timezone = TimeZone.getTimeZone("US/Eastern");

  @Builder.Default
  @JsonIgnore
  private Map<String, String> aliases = new HashMap<>();

  @JsonIgnore
  private String enricher;

  @JsonIgnore
  public boolean inMemory() {
    return code.equalsIgnoreCase("MOCK");
  }
}
