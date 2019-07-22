package com.beancounter.marketdata.providers;

import com.beancounter.common.model.Asset;
import com.beancounter.marketdata.service.MarketDataProvider;
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
  private int maxBatch;
  private Integer currentBatch = 0;
  private String searchBatch = null;
  private String delimiter = ",";
  private Map<String, Asset> dpToBc = new HashMap<>();
  private Map<Asset, String> bcToDp = new HashMap<>();
  private String date;
  /**
   * How the MarketDataProvider wants the search key for all assets to be passed.
   */
  private Map<Integer, String> batch = new HashMap<>();

  public ProviderArguments(int maxBatch) {
    this.maxBatch = maxBatch;
  }

  /**
   * Helper to build an instance of this class based on the supplied arguments.
   *
   * @param assets             Assets being requested
   * @param marketDataProvider Who our provider is
   * @return This class with various keys cross indexed for convenience
   */
  public static ProviderArguments getInstance(Collection<Asset> assets,
                                              MarketDataProvider marketDataProvider) {
    ProviderArguments providerArguments = new ProviderArguments(marketDataProvider.getBatchSize());

    providerArguments.setDate(marketDataProvider.getDate());

    for (Asset asset : assets) {

      String marketCode = marketDataProvider.getMarketProviderCode(asset.getMarket());
      String assetCode = asset.getCode();

      if (marketCode != null && !marketCode.isEmpty()) {
        assetCode = assetCode + "." + marketCode;
      }
      providerArguments.addAsset(asset, assetCode);

    }
    return providerArguments;
  }

  /**
   * Tracks and batches the asset for the DataProvider.
   *
   * @param bcKey BeanCounter Asset
   * @param dpKey DataProvider Asset ID
   */
  public void addAsset(Asset bcKey, String dpKey) {
    dpToBc.put(dpKey, bcKey);
    bcToDp.put(bcKey, dpKey);
    String searchKey = getBatch().get(currentBatch);
    if (searchKey == null) {
      searchKey = dpKey;
    } else {
      searchKey = searchKey + delimiter + dpKey;
    }
    getBatch().put(currentBatch, searchKey);
    count++;
    if (count >= maxBatch) {
      count = 0;
      currentBatch++;
    }
  }

  public String[] getAssets(Integer batchId) {
    return batch.get(batchId).split(delimiter);
  }
}
