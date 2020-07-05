package com.beancounter.client.sharesight;

import com.beancounter.client.ingest.AssetIngestService;
import com.beancounter.client.ingest.Filter;
import com.beancounter.client.ingest.FxTransactions;
import com.beancounter.common.utils.DateUtils;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    DateUtils.class,
    ShareSightDividendAdapter.class,
    ShareSightTradeAdapter.class,
    ShareSightFactory.class,
    FxTransactions.class,
    AssetIngestService.class,
    Filter.class,
    ShareSightRowAdapter.class
})
public class ShareSightConfig {

  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  @Value("${date.format:dd/MM/yyyy}")
  private String dateFormat;

  // Backfill FX rates ignoring source file values
  @Value("${rates:true}")
  private boolean calculateRates = true;

  // Calculate the tradeAmount field and ignore source file value
  @Value("${amount:true}")
  private boolean calculateAmount = true;

  public NumberFormat getNumberFormat() {
    return numberFormat;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public boolean isCalculateRates() {
    return calculateRates;
  }

  public boolean isCalculateAmount() {
    return calculateAmount;
  }
}
