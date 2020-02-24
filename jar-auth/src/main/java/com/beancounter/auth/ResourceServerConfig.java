package com.beancounter.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@ComponentScan("com.beancounter.auth")
public class ResourceServerConfig extends WebSecurityConfigurerAdapter {
  private final JwtRoleConverter jwtRoleConverter;
  @Value("${auth.pattern:/**}")
  private String authPattern;

  public ResourceServerConfig(JwtRoleConverter jwtRoleConverter) {
    this.jwtRoleConverter = jwtRoleConverter;
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.cors().and()
        .authorizeRequests()
        // Scope permits access to the API - basically, "caller is authorised"
        .mvcMatchers(authPattern).hasAuthority("SCOPE_beancounter")
        .anyRequest().permitAll()
        .and()
        .oauth2ResourceServer()
        .jwt()
        // User roles are carried in the claims and used for fine grained control
        //  These roles are extracted using JwtRoleConverter from the {token}.claims.realm_access
        .jwtAuthenticationConverter(jwtRoleConverter);
  }

}