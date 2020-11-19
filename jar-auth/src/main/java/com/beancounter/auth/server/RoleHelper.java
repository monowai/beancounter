package com.beancounter.auth.server;

import com.beancounter.common.model.SystemUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class RoleHelper {
  public static final String ROLE_USER = "ROLE_user";
  public static final String ROLE_ADMIN = "ROLE_admin";
  public static final String ROLE_M2M = "ROLE_m2m";

  public static final SimpleGrantedAuthority AUTH_M2M =
      new SimpleGrantedAuthority(RoleHelper.ROLE_M2M);


  public static final String SCOPE_BC = "SCOPE_beancounter";

  public static final String OAUTH_USER = "user";
  public static final String OAUTH_M2M = "m2m";
  public static final SystemUser m2mSystemUser = new SystemUser(OAUTH_M2M);

  public static final String OAUTH_ADMIN = "admin";
  public static final String SCOPE = "beancounter profile email";
}
