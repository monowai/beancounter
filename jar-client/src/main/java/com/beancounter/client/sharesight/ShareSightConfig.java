package com.beancounter.client.sharesight;

import com.beancounter.client.ingest.AssetIngestService;
import com.beancounter.client.ingest.Filter;
import com.beancounter.client.ingest.FxTransactions;
import com.beancounter.common.utils.DateUtils;
import java.text.NumberFormat;
import java.util.Locale;
import lombok.Data;
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
@Data
public class ShareSightConfig {

  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  @Value("${date.format:dd/MM/yyyy}")
  private String dateFormat;

  @Value("${rates:true}")
  // Backfill FX rates and ignore source file value
  private boolean calculateRates = true;

  @Value("${amount:true}")
  // Calculate the tradeAmount field and ignore source file value
  private boolean calculateAmount = true;


}
