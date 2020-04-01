package com.beancounter.common.contracts;

import com.beancounter.common.model.Trn;
import java.util.ArrayList;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class TrnResponse implements Payload<Collection<Trn>> {
  @Builder.Default
  private Collection<Trn> data = new ArrayList<>();
}
