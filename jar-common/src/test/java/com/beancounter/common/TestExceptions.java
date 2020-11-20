package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.exception.ForbiddenException;
import com.beancounter.common.exception.RecordFailurePredicate;
import com.beancounter.common.exception.SpringExceptionMessage;
import com.beancounter.common.exception.SpringFeignDecoder;
import com.beancounter.common.exception.SystemException;
import com.beancounter.common.exception.UnauthorizedException;
import com.beancounter.common.utils.BcJson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.nio.charset.Charset;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TestExceptions {
  private final RequestTemplate requestTemplate = new RequestTemplate();

  @Test
  void is_FeignBusinessExceptionThrown() {
    SpringFeignDecoder springFeignDecoder = new SpringFeignDecoder();
    Response response = Response.builder()
        .reason("Business Logic")
        .status(HttpStatus.BAD_REQUEST.value())
        .request(Request.create(
            Request.HttpMethod.GET, "/test",
            new HashMap<>(), Request.Body.empty(), requestTemplate))
        .build();
    assertThrows(BusinessException.class, () -> validBusinessException(
        springFeignDecoder.decode("test", response)));

  }

  private void validBusinessException(Exception e) throws Exception {
    assertThat(e).hasFieldOrPropertyWithValue("detailMessage",
        "Business Logic");
    throw e;
  }

  @Test
  void is_FeignSystemExceptionThrown() {
    SpringFeignDecoder springFeignDecoder = new SpringFeignDecoder();
    Response response = Response.builder()
        .reason("Integration Error")
        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
        .request(Request.create(
            Request.HttpMethod.GET, "/test", new HashMap<>(),
            Request.Body.empty(), requestTemplate))
        .build();
    assertThrows(SystemException.class, () -> validSystemException(
        springFeignDecoder.decode("test", response)));

  }

  @Test
  void is_FeignExceptionThrown() {
    SpringFeignDecoder springFeignDecoder = new SpringFeignDecoder();
    Response response = Response.builder()
        .reason("Integration Error")
        .status(HttpStatus.SWITCHING_PROTOCOLS.value())
        .request(Request.create(
            Request.HttpMethod.GET, "/test", new HashMap<>(),
            Request.Body.empty(), requestTemplate))
        .build();

    assertThrows(FeignException.class, () -> {
      Exception e = springFeignDecoder.decode("test", response);
      assertThat(e.getMessage()).contains("101 Integration Error");
      throw e;
    });
  }

  @Test
  void is_AuthExceptionThrown() {
    SpringFeignDecoder springFeignDecoder = new SpringFeignDecoder();
    String reason = "Unauthorized";
    Response response = Response.builder()
        .reason(reason)
        .status(HttpStatus.UNAUTHORIZED.value())
        .request(Request.create(
            Request.HttpMethod.GET, "/test", new HashMap<>(),
            Request.Body.empty(), requestTemplate))
        .build();

    assertThrows(UnauthorizedException.class, () -> {
      Exception e = springFeignDecoder.decode("test", response);
      assertThat(e.getMessage()).contains(reason);
      throw (e);
    });
  }

  @Test
  void is_ForbiddenExceptionThrown() {
    SpringFeignDecoder springFeignDecoder = new SpringFeignDecoder();
    String reason = "Forbidden";
    Response response = Response.builder()
        .reason(reason)
        .status(HttpStatus.FORBIDDEN.value())
        .request(Request.create(
            Request.HttpMethod.GET, "/test", new HashMap<>(),
            Request.Body.empty(), requestTemplate))
        .build();

    assertThrows(ForbiddenException.class, () -> {
      Exception e = springFeignDecoder.decode("test", response);
      assertThat(e.getMessage()).contains(reason);
      throw (e);
    });
  }

  private void validSystemException(Exception e) throws Exception {
    assertThat(e).hasFieldOrPropertyWithValue("detailMessage", "Integration Error");
    throw e;
  }

  @Test
  void is_ServiceIntegrationErrorDecoded() throws JsonProcessingException {
    SpringFeignDecoder springFeignDecoder = new SpringFeignDecoder();
    SpringExceptionMessage springExceptionMessage = new SpringExceptionMessage();

    springExceptionMessage.setMessage("Integration Error");
    Response response = Response.builder()
        .reason("Integration Reason")
        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
        .request(Request.create(
            Request.HttpMethod.GET, "/test", new HashMap<>(),
            Request.Body.empty(), requestTemplate))
        .body(BcJson.getObjectMapper().writeValueAsString(springExceptionMessage),
            Charset.defaultCharset())
        .build();
    assertThrows(SystemException.class, () -> validIntegrationException(
        springFeignDecoder.decode("test", response)));

  }

  private void validIntegrationException(Exception e) throws Exception {
    assertThat(e)
        .hasFieldOrPropertyWithValue("detailMessage", "Integration Error");
    throw e;
  }

  @Test
  void is_ExceptionsBodiesCorrect() {
    assertThrows(BusinessException.class, this::throwBusinessException);
    assertThrows(SystemException.class, this::throwSystemException);
  }

  @Test
  void is_PredicateAssumptions() {
    RecordFailurePredicate recordFailurePredicate = new RecordFailurePredicate();
    assertThat(recordFailurePredicate.test(new BusinessException("User Error"))).isFalse();
    assertThat(recordFailurePredicate.test(new SystemException("System Error"))).isTrue();
  }

  @Test
  void is_SpringErrorSerializable() throws Exception {
    SpringExceptionMessage springExceptionMessage = new SpringExceptionMessage();
    springExceptionMessage.setMessage("Message");
    springExceptionMessage.setStatus(418);
    springExceptionMessage.setPath("/test");
    springExceptionMessage.setError("I'm a teapot");

    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(springExceptionMessage);
    SpringExceptionMessage fromJson = mapper.readValue(json, SpringExceptionMessage.class);
    assertThat(fromJson).usingRecursiveComparison().isEqualTo(springExceptionMessage);
  }


  private void throwBusinessException() {
    BusinessException businessException =
        new BusinessException("Test Message");
    assertThat(businessException)
        .hasFieldOrPropertyWithValue("detailMessage", "Test Message");

    throw businessException;
  }

  private void throwSystemException() {
    SystemException systemException = new SystemException("Test Message");
    assertThat(systemException)
        .hasFieldOrPropertyWithValue("detailMessage", "Test Message");

    throw systemException;
  }
}
