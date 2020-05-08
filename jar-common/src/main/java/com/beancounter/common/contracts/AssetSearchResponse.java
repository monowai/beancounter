package com.beancounter.common.contracts;

import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class AssetSearchResponse implements Payload<Collection<AssetSearchResult>> {
  private Collection<AssetSearchResult> data;
}
