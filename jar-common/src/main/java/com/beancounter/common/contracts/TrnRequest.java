package com.beancounter.common.contracts;


import com.beancounter.common.model.Trn;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrnRequest {
  @Singular
  private Collection<Trn> trns;
  @NonNull
  private String porfolioId;
}
