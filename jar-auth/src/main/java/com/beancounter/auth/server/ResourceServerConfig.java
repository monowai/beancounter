package com.beancounter.auth.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@ComponentScan("com.beancounter.auth.server")
public class ResourceServerConfig extends WebSecurityConfigurerAdapter {
  private final JwtRoleConverter jwtRoleConverter;
  @Value("${auth.pattern:/api/**}")
  private String authPattern;

  @Value("${management.server.servlet.context-path:/management}")
  private String actuatorPattern;

  public ResourceServerConfig(JwtRoleConverter jwtRoleConverter) {
    this.jwtRoleConverter = jwtRoleConverter;
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.cors().and()
        .authorizeRequests()
        // Scope permits access to the API - basically, "caller is authorised"
        .mvcMatchers(actuatorPattern + "/actuator/**").hasRole(RoleHelper.OAUTH_ADMIN)
        .mvcMatchers(authPattern).hasAuthority(RoleHelper.SCOPE_BC)
        .anyRequest().authenticated()
        .and()
        .oauth2ResourceServer()
        .jwt()
        // User roles are carried in the claims and used for fine grained control
        //  These roles are extracted using JwtRoleConverter from the {token}.claims.realm_access
        .jwtAuthenticationConverter(jwtRoleConverter);
  }

}