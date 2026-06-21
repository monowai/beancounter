package com.beancounter.common.client

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.exception.SystemException
import com.beancounter.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.ResponseErrorHandler
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * Error handler for RestClient that maps HTTP errors to custom exceptions.
 * Ported from SpringFeignDecoder.
 */
class RestClientErrorHandler : ResponseErrorHandler {
    private val log = LoggerFactory.getLogger(RestClientErrorHandler::class.java)

    companion object {
        private const val CLIENT_ERROR_MAX = 499
        private const val SERVER_ERROR_MAX = 599
    }

    override fun hasError(response: ClientHttpResponse): Boolean = response.statusCode.isError

    override fun handleError(
        uri: URI,
        httpMethod: HttpMethod,
        response: ClientHttpResponse
    ) {
        val statusCode = response.statusCode.value()
        val reason =
            try {
                response.body.bufferedReader(StandardCharsets.UTF_8).readText().ifBlank {
                    response.statusText
                }
            } catch (e: IOException) {
                log.error("Error reading response body", e)
                "Failed to read response body"
            }

        // 4xx is caller fault (bad id, missing portfolio, expired token). Logged
        // at WARN so it doesn't trip Sentry's ERROR filter — callers that
        // probe-and-fallback (e.g. PerformanceController.resolvePortfolio) would
        // otherwise generate a Sentry event per fallback. 5xx is a real
        // server-side problem and stays at ERROR.
        if (response.statusCode.is4xxClientError) {
            log.warn("HTTP error [$statusCode] $reason")
        } else {
            log.error("HTTP error [$statusCode] $reason")
        }

        throw when (statusCode) {
            HttpStatus.UNAUTHORIZED.value() -> UnauthorizedException(reason)
            HttpStatus.FORBIDDEN.value() -> ForbiddenException(reason)
            HttpStatus.NOT_FOUND.value() -> NotFoundException(reason)
            in HttpStatus.BAD_REQUEST.value()..CLIENT_ERROR_MAX -> BusinessException(reason)
            in HttpStatus.INTERNAL_SERVER_ERROR.value()..SERVER_ERROR_MAX -> SystemException(reason)
            else -> SystemException("HTTP $statusCode: $reason")
        }
    }
}