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
        val reason: String? =
            try {
                getMessage(response)
            } catch (e: IOException) {
                throw SystemException(e.message)
            }
        log.error("$methodKey - [${response.status()}] $reason")
        if (response.status() == HttpStatus.UNAUTHORIZED.value()) {
            // Clearly communicate an authentication issue
            return UnauthorizedException(reason)
        }
        if (response.status() == HttpStatus.FORBIDDEN.value()) {
            // You can't touch this
            return ForbiddenException(reason)
        }
        if (response.status() in 400..499) {
            // We don't want business logic exceptions to flip circuit breakers
            return BusinessException(reason)
        }
        return if (response.status() in 500..599) {
            SystemException(reason)
        } else {
            FeignException.errorStatus(methodKey, response)
        }
    }

    @Throws(IOException::class)
    private fun getMessage(response: Response): String? {
        if (response.body() == null) {
            return response.reason()
        }
        return CharStreams.toString(response.body().asReader(StandardCharsets.UTF_8))
    }
}
