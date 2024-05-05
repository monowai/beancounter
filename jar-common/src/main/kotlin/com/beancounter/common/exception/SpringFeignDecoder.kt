package com.beancounter.common.exception

import com.google.common.io.CharStreams
import feign.FeignException
import feign.Response
import feign.codec.ErrorDecoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Handle deserialization of errors into BusinessException and SystemExceptions.
 * Helpers to extract error messages from Spring MVC exceptions
 *
 * @author mikeh
 * @since 2019-02-03
 */
@Component
class SpringFeignDecoder : ErrorDecoder {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(SpringFeignDecoder::class.java)
    }

    override fun decode(
        methodKey: String,
        response: Response,
    ): Exception {
        val reason =
            try {
                getMessage(response) ?: "No response body"
            } catch (e: IOException) {
                log.error("Error reading response body", e)
                return SystemException("Failed to read response body")
            }

        log.error("$methodKey - [${response.status()}] $reason")

        return when (response.status()) {
            HttpStatus.UNAUTHORIZED.value() -> UnauthorizedException(reason)
            HttpStatus.FORBIDDEN.value() -> ForbiddenException(reason)
            in 400..499 -> BusinessException(reason)
            in 500..599 -> SystemException(reason)
            else -> FeignException.errorStatus(methodKey, response)
        }
    }

    private fun getMessage(response: Response): String? =
        response.body()?.let {
            CharStreams.toString(it.asReader(StandardCharsets.UTF_8))
        } ?: response.reason()
}
