package com.beancounter.common.exception;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Concrete view of the exception structure we return between services.
 * Base on the Spring exception structure
 * @author mikeh
 * @since 2019-02-04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpringExceptionMessage {

  @Builder.Default
  private Date timestamp = new Date();
  private int status;
  private String error;
  private String message;
  private String path;

}
