package com.beancounter.marketdata.batch;

import com.beancounter.marketdata.providers.PriceRefresh;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableScheduling
@EnableTransactionManagement

@Service
@Slf4j
public class ScheduledValuation {
  private final PriceRefresh priceRefresh;

  public ScheduledValuation(PriceRefresh priceRefresh) {
    this.priceRefresh = priceRefresh;
  }

  @Scheduled(cron = "${beancounter.assets.schedule:0 */30 7-18 ? * Tue-Sat}")
  public void updatePrices() {
    priceRefresh.updatePrices();
  }

}
