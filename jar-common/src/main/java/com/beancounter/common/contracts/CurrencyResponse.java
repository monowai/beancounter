package com.beancounter.common.contracts;

import com.beancounter.common.model.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class CurrencyResponse implements Payload<Iterable<Currency>> {
  private Iterable<Currency> data;
}
