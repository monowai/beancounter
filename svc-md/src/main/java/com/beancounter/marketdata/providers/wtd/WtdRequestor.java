package com.beancounter.marketdata.providers.wtd;

import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

/**
 * Async requestor to obtain MarketData.
 *
 * @author mikeh
 * @since 2019-03-06
 */

@Service
public class WtdRequestor {

  private WtdRequest wtdRequest;

  @Autowired
  WtdRequestor(WtdRequest wtdRequest) {
    this.wtdRequest = wtdRequest;
  }

  @Async
  Future<WtdResponse> getMarketData(String assets, String date, String apiKey) {
    WtdResponse result = wtdRequest.getMarketDataForAssets(assets, date, apiKey);
    return new AsyncResult<>(result);
  }
}
