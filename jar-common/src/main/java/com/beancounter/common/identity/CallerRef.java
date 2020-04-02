package com.beancounter.common.identity;

import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.KeyGenUtils;
import java.io.Serializable;
import java.util.UUID;
import javax.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uniquely identifies a transaction, within a batch from a data provider.
 *
 * @author mikeh
 * @since 2019-02-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class CallerRef implements Serializable {
  private String provider;
  private String batch;
  private String callerId;

  // Fill in any missing default values from the supplied Id
  public static CallerRef from(CallerRef id) {
    CallerRef result = CallerRef.builder()
        .build();

    result.setProvider(id == null || id.getProvider() == null ? "BC" : id.provider);
    result.setBatch(id == null || id.getBatch() == null ? new DateUtils().today() : id.batch);
    result.setCallerId(id == null || id.getCallerId() == null
        ? KeyGenUtils.format(UUID.randomUUID()) : id.callerId);
    return result;
  }
}
