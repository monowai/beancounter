package com.beancounter.marketdata.providers;

import com.beancounter.common.model.Asset;
import com.beancounter.common.utils.AssetUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * MarketDataProviders have different ways to provide keys. THis helps us track our
 * internal view of an Asset and match it to the dataproviders ID that is returned in a response.
 *
 * @author mikeh
 * @since 2019-03-12
 */
@Data
public class ProviderArguments {
  private int count = 0;
  private DataProviderConfig dataProviderConfig;
  private Integer currentBatch = 0;
  private String searchBatch = null;
  private String delimiter = ",";
  private Map<Integer, BatchConfig> batchConfigs = new HashMap<>();
  private Map<String, Asset> dpToBc = new HashMap<>();
  private Map<Asset, String> bcToDp = new HashMap<>();
  /**
   * How the MarketDataProvider wants the search key for all assets to be passed.
   */
  private Map<Integer, String> batch = new HashMap<>();

  public ProviderArguments(DataProviderConfig dataProviderConfig) {
    this.dataProviderConfig = dataProviderConfig;
  }

  /**
   * Helper to build an instance of this class based on the supplied arguments.
   *
   * @param assets             Assets being requested
   * @param dataProviderConfig Who our provider is
   * @return This class with various keys cross indexed for convenience
   */
  public static ProviderArguments getInstance(Collection<Asset> assets,
                                              DataProviderConfig dataProviderConfig) {
    ProviderArguments providerArguments = new ProviderArguments(dataProviderConfig);

    // Data providers can have market dependent price dates. Batch first by market, then by size
    Map<String, Collection<Asset>> marketAssets = AssetUtils.split(assets);
    for (String market : marketAssets.keySet()) {
      for (Asset asset : marketAssets.get(market)) {

        String marketCode = dataProviderConfig.translateMarketCode(asset.getMarket());
        String assetCode = asset.getCode();

        if (marketCode != null && !marketCode.isEmpty()) {
          assetCode = assetCode + "." + marketCode;
        }
        providerArguments.addAsset(asset, assetCode, dataProviderConfig
            .getMarketDate(asset.getMarket()));

      }
      providerArguments.bumpBatch();
    }

    return providerArguments;
  }

  private void bumpBatch() {
    currentBatch++;
  }

  /**
   * Tracks and batches the asset for the DataProvider.
   *
   * @param bcKey BeanCounter Asset
   * @param dpKey DataProvider Asset ID
   */
  public void addAsset(Asset bcKey, String dpKey, String date) {
    dpToBc.put(dpKey, bcKey);
    bcToDp.put(bcKey, dpKey);
    BatchConfig batchConfig = batchConfigs.get(currentBatch);
    if (batchConfig == null) {
      batchConfig = BatchConfig.builder()
          .batch(currentBatch)
          .date(date)
          .build();
      batchConfigs.put(currentBatch, batchConfig);
    }

    String searchKey = getBatch().get(batchConfig.getBatch());
    if (searchKey == null) {
      searchKey = dpKey;
    } else {
      searchKey = searchKey + delimiter + dpKey;
    }
    getBatch().put(currentBatch, searchKey);
    count++;
    if (count >= dataProviderConfig.getBatchSize()) {
      count = 0;
      currentBatch++;
    }
  }

  public String[] getAssets(Integer batchId) {
    return batch.get(batchId).split(delimiter);
  }
}
