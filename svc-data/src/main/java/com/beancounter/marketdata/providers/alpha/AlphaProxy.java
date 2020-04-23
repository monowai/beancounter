package com.beancounter.marketdata.providers.alpha;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AlphaProxy {
  private AlphaGateway alphaGateway;

  @Autowired(required = false)
  void setAlphaGateway(AlphaGateway alphaGateway) {
    this.alphaGateway = alphaGateway;
  }

  @RateLimiter(name = "alphaVantage") // AV "Free Plan" rate limits
  public String getPrices(String code, String apiKey) {
    return alphaGateway.getPrices(code, apiKey);
  }

  @RateLimiter(name = "alphaVantage") // AV "Free Plan" rate limits
  public String getPrice(String code, String apiKey) {
    return alphaGateway.getPrice(code, apiKey);
  }
}
