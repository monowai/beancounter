package com.beancounter.shell.config;

import com.beancounter.client.ClientConfig;
import com.beancounter.shell.reader.Filter;
import com.beancounter.shell.reader.RowProcessor;
import com.beancounter.shell.sharesight.ShareSightDivis;
import com.beancounter.shell.sharesight.ShareSightService;
import com.beancounter.shell.sharesight.ShareSightTrades;
import com.beancounter.shell.sharesight.ShareSightTransformers;
import com.beancounter.shell.writer.FxTransactions;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    ClientConfig.class,
    ShareSightDivis.class,
    ShareSightTrades.class,
    ShareSightTransformers.class,
    ShareSightService.class,
    FxTransactions.class,
    Filter.class,
    RowProcessor.class
})
public class ShareSightConfig {

}
