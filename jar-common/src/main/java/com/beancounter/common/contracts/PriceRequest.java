package com.beancounter.common.contracts;

import com.beancounter.common.model.Asset;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class PriceRequest {
  private String date;
  private Collection<Asset> assets;
}
