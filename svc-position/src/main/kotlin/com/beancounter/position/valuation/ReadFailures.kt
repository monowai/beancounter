package com.beancounter.position.valuation

import com.beancounter.common.model.Portfolio
import org.slf4j.Logger
import kotlin.coroutines.cancellation.CancellationException

/**
 * [block]'s result. A failure is logged naming the [read] and the [portfolio] it was
 * for, then propagated unchanged; a cancellation passes through unlogged so
 * cooperative cancellation still works.
 */
fun <T> Logger.reading(
    read: String,
    portfolio: Portfolio,
    block: () -> T
): T =
    runCatching(block)
        .onFailure { e ->
            if (e !is CancellationException) {
                error("{} read failed for portfolio {}", read, portfolio.code, e)
            }
        }.getOrThrow()