package com.beancounter.common.identity;

import java.io.Serializable;
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
public class TransactionId implements Serializable {
  private String provider;
  private Integer batch;
  private Integer id;

}
