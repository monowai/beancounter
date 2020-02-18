package com.beancounter.shell.config;

import com.beancounter.shell.reader.Filter;
import com.beancounter.shell.reader.RowProcessor;
import com.beancounter.shell.service.AssetService;
import com.beancounter.shell.service.FxRateService;
import com.beancounter.shell.service.FxTransactions;
import com.beancounter.shell.service.PortfolioService;
import com.beancounter.shell.service.StaticService;
import com.beancounter.shell.sharesight.ShareSightDivis;
import com.beancounter.shell.sharesight.ShareSightService;
import com.beancounter.shell.sharesight.ShareSightTrades;
import com.beancounter.shell.sharesight.ShareSightTransformers;
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
    FxTransactions.class,
    FxRateService.class,
    StaticService.class,
    PortfolioService.class,
    AssetService.class,
    Filter.class,
    RowProcessor.class,
    ExchangeConfig.class,
    ShareSightService.class})
@EnableFeignClients(basePackages = "com.beancounter.shell")
@ImportAutoConfiguration({
    HttpMessageConvertersAutoConfiguration.class,
    FeignAutoConfiguration.class})

public class ShareSightConfig {

}
