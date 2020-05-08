package com.beancounter.marketdata.assets;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;

public interface AssetEnricher {

  Asset enrich(Market market, String code, String defaultName);

  boolean canEnrich(Asset asset);
}
