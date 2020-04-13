package com.beancounter.common.exception;

import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class RestApiException {

  @ExceptionHandler({HttpMessageNotReadableException.class})
  public ResponseEntity<Object> handleBadRequest(HttpServletRequest request) {

    SpringExceptionMessage error = new SpringExceptionMessage();
    error.setError("We did not understand your request. Please reformat it and try again");
    error.setMessage("Message not readable");
    error.setPath(request.getRequestURI());
    error.setStatus(HttpStatus.BAD_REQUEST.value());
    return new ResponseEntity<>(error, new HttpHeaders(), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler({DataIntegrityViolationException.class})
  public ResponseEntity<Object> handleIntegrity(HttpServletRequest request, Throwable e) {

    SpringExceptionMessage error = new SpringExceptionMessage();
    log.debug(e.getMessage());
    error.setError("Constraint violation in request. Work is not accepted");
    error.setMessage("Integrity Violation");
    error.setPath(request.getRequestURI());
    error.setStatus(HttpStatus.BAD_REQUEST.value());
    return new ResponseEntity<>(error, new HttpHeaders(), HttpStatus.BAD_REQUEST);
  }

}