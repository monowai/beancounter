package com.beancounter.shell.sharesight;

import com.beancounter.client.ClientConfig;
import com.beancounter.client.FxTransactions;
import com.beancounter.common.utils.UtilConfig;
import com.beancounter.shell.ingest.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    ClientConfig.class,
    UtilConfig.class,
    ShareSightDivis.class,
    ShareSightTrades.class,
    ShareSightTransformers.class,
    ShareSightService.class,
    FxTransactions.class,
    Filter.class,
    ShareSightRowProcessor.class
})
public class ShareSightConfig {

}
