package com.beancounter.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Classification for logic or constraint failures.
 *
 * @author mikeh
 * @since 2019-02-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ResponseStatus(code = HttpStatus.BAD_REQUEST)
public class BusinessException extends RuntimeException {

  private int status;

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
    super(reason);
    this.status = status;
  }

}