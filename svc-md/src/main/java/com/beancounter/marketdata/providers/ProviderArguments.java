package com.beancounter.marketdata.providers;

import com.beancounter.common.model.Asset;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.util.Dates;
import java.util.Collection;
import java.util.Date;
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
  int count = 0;
  int maxBatch;
  Integer currentBatch = 0;
  String searchBatch = null;
  String delimiter = ",";

  public ProviderArguments(int maxBatch) {
    this.maxBatch = maxBatch;
  }

  /**
   * Helper to build an instance of this class based on the supplied arguments.
   * @param assets  Assets being requested
   * @param date    Price Date
   * @param marketDataProvider Who our provider is
   * @return This class with various keys cross indexed for convenience
   */
  public static ProviderArguments getInstance(Collection<Asset> assets, String date,
                                              MarketDataProvider marketDataProvider) {
    ProviderArguments providerArguments = new ProviderArguments(marketDataProvider.getBatchSize());
    providerArguments.setDate(Dates.getDate(date, null));

    for (Asset asset : assets) {

      String marketCode = marketDataProvider.getMarketProviderCode(asset.getMarket().getCode());
      String assetCode = asset.getCode();

      if (marketCode != null) {
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

  private Map<String, Asset> dpToBc = new HashMap<>();
  private Map<Asset, String> bcToDp = new HashMap<>();
  private Date date;

  /**
   * How the MarketDataProvider wants the search key for all assets to be passed.
   */
  private Map<Integer, String> batch = new HashMap<>();


  public String[] getAssets(Integer batchId) {
    return batch.get(batchId).split(delimiter);
  }
}
