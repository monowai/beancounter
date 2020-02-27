package com.beancounter.common.exception;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;

/**
 * Handle deserialization of errors into BusinessException and SystemExceptions.
 * Helpers to extract error messages from Spring MVC exceptions
 *
 * @author mikeh
 * @since 2019-02-03
 */
public class SpringFeignDecoder implements ErrorDecoder {

  private static ObjectMapper mapper;

  static {
    mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Override
  public Exception decode(String methodKey, Response response) {
    String reason = getMessage(response);
    if (response.status() == HttpStatus.UNAUTHORIZED.value()) {
      // Clearly communicate an authentication issue
      return new UnauthorizedException(reason);
    }
    if (response.status() == HttpStatus.FORBIDDEN.value()) {
      // You can't touch this
      return new ForbiddenException(reason);
    }

    if (response.status() >= 400 && response.status() <= 499) {
      // We don't want business logic exceptions to flip circuit breakers
      return new BusinessException(reason);
    }
    if (response.status() >= 500 && response.status() <= 599) {
      return new SystemException(reason);
    }
    return FeignException.errorStatus(methodKey, response);
  }

  @SneakyThrows
  private String getMessage(Response response) {
    if (response.body() == null) {
      SpringExceptionMessage exceptionMessage = SpringExceptionMessage.builder()
          .message(response.reason())
          .status(response.status())
          .build();
      return exceptionMessage.getMessage();
    }

    try (Reader reader = response.body().asReader(StandardCharsets.UTF_8)) {
      String result = CharStreams.toString(reader);

      //init the Pojo
      SpringExceptionMessage exceptionMessage = mapper.readValue(result,
          SpringExceptionMessage.class);
      return exceptionMessage.getMessage();
    }

  }

}