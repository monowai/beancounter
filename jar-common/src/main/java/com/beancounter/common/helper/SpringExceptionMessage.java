package com.beancounter.common.helper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Concrete view of the exception structure we return between services.
 * Base on the Spring exception structure
 * @author mikeh
 * @since 2019-02-04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
class SpringExceptionMessage {

  private String timestamp;
  private int status;
  private String error;
  private String message;
  private String path;

}
