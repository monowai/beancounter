package com.beancounter.common.contracts;

import com.beancounter.common.model.SystemUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationResponse implements Payload<SystemUser> {
  private SystemUser data;
}
