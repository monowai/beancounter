package com.beancounter.client;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;

public interface AssetService {
  AssetUpdateResponse process(AssetRequest assetRequest);
}
