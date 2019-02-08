package com.beancounter.position.integration;

import java.util.function.Predicate;

/**
 * Something which is affirmed or denied concerning an argument of a proposition.
 *
 * @author mikeh
 * @since 2019-02-03
 */
public class MdFailurePredicate implements Predicate<Throwable> {
    @Override
    public boolean test(Throwable throwable) {
        return true;
    }
}
