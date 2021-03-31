package com.beancounter.auth.client

import feign.codec.Encoder
import feign.form.spring.SpringFormEncoder
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.http.HttpMessageConverters
import org.springframework.cloud.openfeign.support.SpringEncoder
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component

@Component
/**
 * Authentication support beans.
 */
class AuthBeans {
    @Bean
    fun feignFormEncoder(converters: ObjectFactory<HttpMessageConverters>): Encoder {
        return SpringFormEncoder(SpringEncoder(converters))
    }

    @Bean
    fun jwtDecoder(@Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") jwkSetUri: String?): JwtDecoder {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
    }
}
