package com.beancounter.common.helper;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.exception.SystemException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.IOException;
import java.io.Reader;

/**
 * Handle deserialization of errors into BusinessException and SystemExceptions
 * Helpers to extract error messages from Spring MVC exceptions
 * 
 * @author mikeh
 * @since 2019-02-03
 */
public class SpringFeignDecoder implements ErrorDecoder {

    private ObjectMapper mapper ;

    {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        String reason ;
        try {
            reason = getMessage(response);
        } catch (IOException e) {
            return FeignException.errorStatus(methodKey, response);
        }

        if (response.status() >= 400 && response.status() <= 499) {
            // We don't want business logic exceptions to flip circuit breakers
            return new BusinessException(
                response.status(),
                reason
            );
        }
        if (response.status() >= 500 && response.status() <= 599) {
            return new SystemException(
                response.status(),
                reason
            );
        }
        return FeignException.errorStatus(methodKey, response);
    }

    String getMessage ( Response response ) throws IOException {

        try (Reader reader = response.body().asReader()) {
            String result = CharStreams.toString(reader);


            //init the Pojo
            SpringExceptionMessage exceptionMessage = mapper.readValue(result,
                SpringExceptionMessage.class);
            return exceptionMessage.getMessage();
        }

    }

}