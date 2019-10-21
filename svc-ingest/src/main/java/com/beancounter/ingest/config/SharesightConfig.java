package com.beancounter.ingest.config;

import com.beancounter.ingest.reader.Filter;
import com.beancounter.ingest.reader.RowProcessor;
import com.beancounter.ingest.sharesight.ShareSightDivis;
import com.beancounter.ingest.sharesight.ShareSightTrades;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.beancounter.ingest.sharesight.common.ShareSightHelper;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import( {
    ShareSightDivis.class, ShareSightTrades.class, ShareSightTransformers.class, Filter.class,
    RowProcessor.class, ExchangeConfig.class,
    ShareSightHelper.class})
public class SharesightConfig {

}
