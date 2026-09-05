package com.beancounter.marketdata.apikey

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Thrown by [TokenRateLimiter] when a caller exceeds the token-exchange
 * rate limit. Deliberately not routed through jar-common's
 * GlobalExceptionHandler - `@ResponseStatus` is enough for Spring MVC's
 * default `ResponseStatusExceptionResolver` to map this to 429, and no
 * other service needs a 429 mapping (yet).
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
class TooManyRequestsException(
    message: String
) : RuntimeException(message)