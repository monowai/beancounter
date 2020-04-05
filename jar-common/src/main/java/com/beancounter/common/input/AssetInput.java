package com.beancounter.common.input;

import com.beancounter.common.model.Asset;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class AssetInput {
  // Asset code.
  private String code;
  // Market code or alias
  private String market;
  @JsonIgnore // This property is server side only & untrusted from a client
  private Asset resolvedAsset;
}
