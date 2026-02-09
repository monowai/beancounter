package com.beancounter.marketdata.config

import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Handles Resilience4j rate limiter exceptions, returning a proper
 * JSON ProblemDetail response instead of the default error page.
 */
@ControllerAdvice
class RateLimitExceptionHandler {
    @ExceptionHandler(RequestNotPermitted::class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ResponseBody
    fun handleRateLimitExceeded(e: RequestNotPermitted): ProblemDetail =
        ProblemDetail
            .forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                e.message ?: "Rate limit exceeded, please retry later"
            ).also {
                log.warn("Rate limit exceeded: {}", e.message)
            }

    companion object {
        private val log = LoggerFactory.getLogger(RateLimitExceptionHandler::class.java)
    }
}