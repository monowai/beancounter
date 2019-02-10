package com.beancounter.common.identity;

import lombok.Builder;
import lombok.Data;

/**
 * Always useful to keep an object for Id.
 * @author mikeh
 * @since 2019-02-10
 */
@Data
@Builder
public class TransactionId {
  String provider;
  Integer batch;
  Integer id;
}
