package com.beancounter.shell.config;

import com.beancounter.auth.TokenService;
import com.beancounter.shell.auth.AuthBeans;
import com.beancounter.shell.auth.LoginService;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@Import({
    LoginService.class,
    AuthBeans.class,
    TokenService.class,
    NimbusJwtDecoder.class,
    DefaultJWTProcessor.class})
@EnableFeignClients(basePackages = "com.beancounter.shell")
@ImportAutoConfiguration({
    HttpMessageConvertersAutoConfiguration.class,
    FeignAutoConfiguration.class})
public class AuthConfig {
}
