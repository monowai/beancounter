package com.beancounter.auth.client;

import feign.form.spring.SpringFormEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class AuthBeans {
  @Bean
  feign.codec.Encoder feignFormEncoder(ObjectFactory<HttpMessageConverters> converters) {
    return new SpringFormEncoder(new SpringEncoder(converters));
  }

  @Bean
  public JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
                                   String jwkSetUri) {
    return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
  }

}
