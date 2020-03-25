package com.beancounter.shell.config;

import com.beancounter.auth.client.AuthClientConfig;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@Import({
    AuthClientConfig.class,
    NimbusJwtDecoder.class,
    DefaultJWTProcessor.class})
public class AuthTokenConfig {
}
