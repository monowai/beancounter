package com.beancounter.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Classification for logic or constraint failures.
 *
 * @author mikeh
 * @since 2019-02-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {
  private int statusText;

  public BusinessException(String message) {
    super(message);
  }

  /**
   * Typically HTTPStatus 400-499. Logic or constraint failures
   *
   * @author mikeh
   * @since 2019-02-03
   */
  public BusinessException(int status, String reason) {
    this(reason);
    this.statusText = status;
  }

}