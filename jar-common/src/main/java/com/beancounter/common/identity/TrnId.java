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
public class TrnId implements Serializable {
  private String provider;
  private String batch;
  private String id;

  public static TrnId from(TrnId id) {
    TrnId result = TrnId.builder()
        .build();

    result.setProvider(id == null || id.getProvider() == null ? "BC" : id.provider);
    result.setBatch(id == null || id.getBatch() == null ? new DateUtils().today() : id.batch);
    result.setId(id == null || id.getId() == null ? KeyGenUtils.format(UUID.randomUUID()) : id.id);
    return result;
  }
}
