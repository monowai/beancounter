package com.beancounter.common.exception

import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.util.DisconnectedClientHelper
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

    @ExceptionHandler(NotFoundException::class)
    @ResponseBody
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(e: NotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            e.message ?: "Resource not found"
        )

    @ExceptionHandler(
        ConnectException::class,
        ResourceAccessException::class,
        RestClientException::class
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
    fun handleBusinessException(e: RuntimeException): ProblemDetail =
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

    /**
     * Last resort for anything no handler above maps, so an unexpected 500 is still logged and
     * sent to Sentry. Exceptions Spring already maps are rethrown untouched: rethrowing the same
     * exception tells the resolver chain this advice did not handle it, so framework 4xx,
     * `@ResponseStatus` exceptions and the security filter chain keep their status. A client that
     * hung up is not a server fault, so it is not reported either.
     */
    @ExceptionHandler(Throwable::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    fun handleUnexpected(e: Throwable): ProblemDetail {
        if (isMappedElsewhere(e)) {
            throw e
        }
        return handleSystemException(e)
    }

    private fun isMappedElsewhere(e: Throwable): Boolean =
        e is ErrorResponse ||
            AnnotatedElementUtils.hasAnnotation(e.javaClass, ResponseStatus::class.java) ||
            isSecurityException(e) ||
            DisconnectedClientHelper.isClientDisconnectedException(e)

    // Matched by name: jar-common does not depend on Spring Security.
    private fun isSecurityException(e: Throwable): Boolean =
        generateSequence<Class<*>>(e.javaClass) { it.superclass }
            .any { it.name.startsWith("org.springframework.security.") }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}