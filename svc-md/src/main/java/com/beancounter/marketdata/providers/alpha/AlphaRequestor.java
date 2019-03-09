package com.beancounter.marketdata.providers.alpha;

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
public class AlphaRequestor {

  private AlphaRequest alphaRequest;

  @Autowired
  AlphaRequestor(AlphaRequest alphaRequest) {
    this.alphaRequest = alphaRequest;
  }

  @Async
  Future<String> getMarketData(String code, String apiKey) {
    String result = alphaRequest.getMarketData(code, apiKey);
    return new AsyncResult<>(result);
  }
}
