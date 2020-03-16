package com.beancounter.common.contracts;

import com.beancounter.common.input.TrnInput;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrnRequest implements Payload<Collection<TrnInput>> {

  private Collection<TrnInput> data;
  @NonNull
  private String portfolioId;
}
