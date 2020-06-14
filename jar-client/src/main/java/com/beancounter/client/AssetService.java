package com.beancounter.client;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.model.Asset;

public interface AssetService {
  AssetUpdateResponse process(AssetRequest assetRequest);

  void backFillEvents(Asset asset);
}
