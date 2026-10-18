package com.beancounter.marketdata.providers.sec

/**
 * SEC answered a throttle status (403 or 429) for its company index, or did so recently enough
 * that [SecTickerResolver] declines to ask again yet.
 */
class SecThrottledException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)