package com.beancounter.position.integration;

import com.beancounter.common.exception.SpringExceptionMessage;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class RestApiException {

  @ExceptionHandler( {HttpMessageNotReadableException.class})
  public ResponseEntity<Object> handleBadRequest(HttpServletRequest request) {

    SpringExceptionMessage error = new SpringExceptionMessage();
    error.setError("We did not understand your request. Please reformat it and try again");
    error.setMessage("Message not readable");
    error.setPath(request.getRequestURI());
    error.setStatus(HttpStatus.BAD_REQUEST.value());
    return new ResponseEntity<>(
        error, new HttpHeaders(), HttpStatus.BAD_REQUEST);
  }

}