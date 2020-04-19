package com.beancounter.marketdata.providers.alpha;

import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

/**
 * Async proxy to obtain MarketData from Alpha.
 *
 * @author mikeh
 * @since 2019-03-06
 */

@Service
public class AlphaProxy {

  private AlphaGateway alphaGateway;

  @Autowired(required = false)
  void setAlphaGateway(AlphaGateway alphaGateway) {
    this.alphaGateway = alphaGateway;
  }

  @Cacheable("asset.prices")
  @Async
  public Future<String> getPrice(String code, String apiKey) {
    return new AsyncResult<>(alphaGateway.getPrice(code, apiKey));
  }

  @Async
  @Cacheable("asset.prices")
  public Future<String> getPrices(String code, String apiKey) {
    String result = alphaGateway.getPrices(code, apiKey);
    return new AsyncResult<>(result);
  }
}
