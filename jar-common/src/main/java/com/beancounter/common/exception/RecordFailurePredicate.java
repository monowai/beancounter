package com.beancounter.common.exception;

import java.util.function.Predicate;

/**
 * Test to determine if a business exception has been detected or a system exception.
 * System exceptions can trigger circuit breakers, business exceptions will not.
 *
 * @author mikeh
 * @since 2019-02-03
 */
@SuppressWarnings("unused")
public class RecordFailurePredicate implements Predicate<Throwable> {

  @Override
  public boolean test(Throwable throwable) {
    return !(throwable instanceof BusinessException);
  }

}