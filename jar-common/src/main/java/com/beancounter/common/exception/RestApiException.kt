package com.beancounter.common.exception

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.util.*
import javax.servlet.http.HttpServletRequest

@ControllerAdvice
class RestApiException {
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(request: HttpServletRequest, e: Throwable): ResponseEntity<Any> {
        val error = SpringExceptionMessage(
                Date(),
                HttpStatus.BAD_REQUEST.value(),
                "We are unable to process your request.",
                e.message, request.requestURI

        )
        return ResponseEntity(error, HttpHeaders(), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleBadRequest(request: HttpServletRequest): ResponseEntity<Any> {
        val error = SpringExceptionMessage(
                Date(),
                HttpStatus.BAD_REQUEST.value(),
                "We did not understand your request.",
                "Message not readable", request.requestURI)

        return ResponseEntity(error, HttpHeaders(), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleIntegrity(request: HttpServletRequest, e: Throwable): ResponseEntity<Any> {
        val error = SpringExceptionMessage(
                Date(),
                HttpStatus.CONFLICT.value(),
                "Request rejected.",
                "Data integrity violation", request.requestURI)
        return ResponseEntity(error, HttpHeaders(), HttpStatus.BAD_REQUEST)
    }

}