package com.beancounter.client.sharesight;

import com.beancounter.client.ingest.Filter;
import com.beancounter.client.services.ClientConfig;
import com.beancounter.client.services.FxTransactions;
import com.beancounter.common.utils.UtilConfig;
import java.text.NumberFormat;
import java.util.Locale;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    ClientConfig.class,
    UtilConfig.class,
    ShareSightDividendAdapter.class,
    ShareSightTradeAdapter.class,
    ShareSightFactory.class,
    FxTransactions.class,
    Filter.class,
    ShareSightRowAdapter.class
})
@Data
public class ShareSightConfig {

  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  @Value("${date.format:dd/MM/yyyy}")
  private String dateFormat;

  @Value("${ratesIgnored:false}")
  private boolean ratesIgnored = false; // Use rates in source file to compute values, but have BC


}
