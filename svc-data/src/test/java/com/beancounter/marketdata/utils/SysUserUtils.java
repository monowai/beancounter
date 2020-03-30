package com.beancounter.marketdata.utils;

import com.beancounter.common.model.SystemUser;
import java.util.UUID;

public class SysUserUtils {
  public static SystemUser getSystemUser() {
    return SystemUser.builder()
        .id(UUID.randomUUID().toString())
        .build();
  }

}
