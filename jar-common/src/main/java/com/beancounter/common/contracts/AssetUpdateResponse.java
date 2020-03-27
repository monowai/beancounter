package com.beancounter.common.contracts;

import com.beancounter.common.model.Asset;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
/*
 * In response to upsert request for a bunch of assets.
 * The supplied identifier can be used by the caller find the asset result in the response.
 */
public class AssetUpdateResponse implements Payload<Map<String, Asset>> {
  @Singular("data")
  private Map<String, Asset> data;
}