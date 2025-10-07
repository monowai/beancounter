package com.beancounter.common.exception

import feign.FeignException
import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.ResourceAccessException
import java.net.ConnectException

/**
 * When an exception is thrown, it is intercepted by this class and a JSON friendly response is returned.
 */
@ControllerAdvice
class GlobalExceptionHandler(
    @param:Value($$"${sentry.enabled:false}") val sentryEnabled: Boolean = false
) {
    @ExceptionHandler(UnauthorizedException::class)
    @ResponseBody
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleUnauthorized(e: UnauthorizedException): ProblemDetail {
        log.debug("Unauthorized access attempt: {}", e.message)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
            e.message ?: "Authentication required"
        )
    }

    @ExceptionHandler(
        AccessDeniedException::class,
        ForbiddenException::class
    )
    @ResponseBody
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleAccessDenied(e: Throwable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            e.message ?: "Access Denied"
        )

    @ExceptionHandler(
        ConnectException::class,
        ResourceAccessException::class,
        FeignException::class
    )
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    fun handleSystemException(e: Throwable): ProblemDetail =
        ProblemDetail
            .forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.message ?: "Unexpected issue"
            ).also {
                log.error(
                    e.message,
                    e
                )
                if (sentryEnabled) {
                    Sentry.captureException(e)
                }
            }

    private val errorMessage = "We are unable to process your request."

    @ExceptionHandler(
        BusinessException::class,
        IllegalArgumentException::class
    )
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun handleBusinessException(e: BusinessException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            e.message ?: errorMessage
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun handleBadRequest(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            errorMessage
        )

    @ExceptionHandler(DataIntegrityViolationException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    fun handleIntegrity(e: Throwable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            e.message ?: "Data integrity violation"
        )

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}