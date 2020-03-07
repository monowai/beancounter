package com.beancounter.shell.config;

import com.beancounter.auth.TokenService;
import com.beancounter.client.RegistrationService;
import com.beancounter.shell.auth.AuthBeans;
import com.beancounter.shell.auth.LoginService;
import com.beancounter.shell.cli.UserCommands;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    EnvConfig.class,
    LoginService.class,
    UserCommands.class,
    RegistrationService.class,
    LoginService.class,
    AuthBeans.class,
    TokenService.class})

@EnableFeignClients(basePackages = "com.beancounter.shell")
@ImportAutoConfiguration({
    HttpMessageConvertersAutoConfiguration.class,
    FeignAutoConfiguration.class})

public class AuthConfig {
}
