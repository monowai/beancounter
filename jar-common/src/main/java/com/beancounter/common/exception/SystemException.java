package com.beancounter.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Classification for integration or other system failures.
 *
 * @author mikeh
 * @since 2019-02-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SystemException extends RuntimeException {
  private int status;

  /**
   * Typically HTTPStatus 500-599. Unexpected failures
   *
   * @author mikeh
   * @since 2019-02-03
   */
  public SystemException(int status, String reason) {
    super(reason);
    this.status = status;
  }

}