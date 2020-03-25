package com.beancounter.client.sharesight;

import com.beancounter.client.ingest.Filter;
import com.beancounter.client.services.ClientConfig;
import com.beancounter.client.services.FxTransactions;
import com.beancounter.common.utils.UtilConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    ClientConfig.class,
    UtilConfig.class,
    ShareSightDividendAdapater.class,
    ShareSightTradeAdapter.class,
    ShareSightFactory.class,
    ShareSightService.class,
    FxTransactions.class,
    Filter.class,
    ShareSightRowAdapter.class
})
public class ShareSightConfig {

}
