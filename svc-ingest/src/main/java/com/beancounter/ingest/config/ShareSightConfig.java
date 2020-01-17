package com.beancounter.ingest.config;

import com.beancounter.ingest.reader.Filter;
import com.beancounter.ingest.reader.RowProcessor;
import com.beancounter.ingest.service.AssetService;
import com.beancounter.ingest.service.BcService;
import com.beancounter.ingest.sharesight.ShareSightDivis;
import com.beancounter.ingest.sharesight.ShareSightService;
import com.beancounter.ingest.sharesight.ShareSightTrades;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    ShareSightDivis.class,
    ShareSightTrades.class,
    ShareSightTransformers.class,
    AssetService.class,
    BcService.class,
    Filter.class,
    RowProcessor.class,
    ExchangeConfig.class,
    ShareSightService.class})
@EnableFeignClients(basePackages = "com.beancounter.ingest")
@ImportAutoConfiguration({
    HttpMessageConvertersAutoConfiguration.class,
    FeignAutoConfiguration.class})

public class ShareSightConfig {

}
