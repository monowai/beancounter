package com.beancounter.marketdata.providers.wtd;

import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.providers.BatchConfig;
import com.beancounter.marketdata.providers.MarketDataAdapter;
import com.beancounter.marketdata.providers.ProviderArguments;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WtdAdapter implements MarketDataAdapter {

  private final DateUtils dateUtils = new DateUtils();

  public Collection<MarketData> get(ProviderArguments providerArguments,
                                    Integer batchId, Future<WtdResponse> response) {
    Collection<MarketData> results = new ArrayList<>();
    try {
      WtdResponse wtdResponse = response.get();

      String[] assets = providerArguments.getAssets(batchId);
      BatchConfig batchConfig = providerArguments.getBatchConfigs().get(batchId);
      for (String dpAsset : assets) {

        // Ensure we return a MarketData result for each requested asset
        Asset bcAsset = providerArguments.getDpToBc().get(dpAsset);
        if (wtdResponse.getMessage() != null) {
          // Issue with the data
          // ToDo: subtract a day and try again?
          log.trace("{} - {}", wtdResponse.getMessage(), providerArguments.getAssets(batchId));
        }

        MarketData marketData = null;
        if (wtdResponse.getData() != null) {
          marketData = wtdResponse.getData().get(dpAsset);
        }

        if (marketData == null) {
          // Not contained in the response
          marketData = getDefault(bcAsset, dpAsset, batchConfig);
        } else {
          marketData.setAsset(bcAsset);
        }

        if (marketData.getPriceDate() == null) {
          marketData.setPriceDate(dateUtils.getDate(wtdResponse.getDate()));
        }
        results.add(marketData);
      }
      return results;
    } catch (InterruptedException | ExecutionException e) {
      throw new SystemException(e.getMessage());
    }
  }


  private MarketData getDefault(Asset asset, String dpAsset, BatchConfig batchConfig) {
    log.trace("{}/{} - unable to locate a price on {}",
        dpAsset, asset.getName(), batchConfig.getDate());

    return MarketData.builder()
        .asset(asset)
        .close(BigDecimal.ZERO).build();
  }
}
