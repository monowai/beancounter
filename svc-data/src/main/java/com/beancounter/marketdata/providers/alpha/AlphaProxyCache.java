package com.beancounter.marketdata.providers.alpha;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async proxy to obtain MarketData from Alpha.
 *
 * @author mikeh
 * @since 2019-03-06
 */

@Service
public class AlphaProxyCache {

  private AlphaProxy alphaProxy;

  @Autowired(required = false)
  void setAlphaProxy(AlphaProxy alphaProxy) {
    this.alphaProxy = alphaProxy;
  }

  @Cacheable("asset.prices")
  @Async
  // Date is to support caching
  public String getCurrent(String code, String date, String apiKey) {
    return alphaProxy.getCurrent(code, apiKey);
  }

  @SuppressWarnings("unused")
  @Cacheable("asset.prices")
  public String getHistoric(String code, String date, String apiKey) {
    return alphaProxy.getHistoric(code, apiKey);
  }
}
