package com.beancounter.common.exception

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.common.io.CharStreams
import feign.FeignException
import feign.Response
import feign.codec.ErrorDecoder
import org.springframework.http.HttpStatus
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Handle deserialization of errors into BusinessException and SystemExceptions.
 * Helpers to extract error messages from Spring MVC exceptions
 *
 * @author mikeh
 * @since 2019-02-03
 */
class SpringFeignDecoder : ErrorDecoder {
    companion object {
        private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    }

    override fun decode(methodKey: String, response: Response): Exception {
        val reason: String?
        reason = try {
            getMessage(response)
        } catch (e: IOException) {
            throw SystemException(e.message)
        }
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
        } else
            FeignException.errorStatus(methodKey, response)
    }

    @Throws(IOException::class)
    private fun getMessage(response: Response): String? {
        if (response.body() == null) {
            val exceptionMessage = SpringExceptionMessage()
            exceptionMessage.message = response.reason()
            exceptionMessage.status = response.status()
            return exceptionMessage.message
        }
        response.body().asReader(StandardCharsets.UTF_8)
                .use { reader ->
                    val result = CharStreams.toString(reader)

                    //init the Pojo
                    val exceptionMessage = mapper.readValue(result,
                            SpringExceptionMessage::class.java)
                    return exceptionMessage.message
                }
    }
}