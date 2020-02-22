package com.beancounter.client;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    AssetService.class,
    FxRateService.class,
    StaticService.class,
    PriceService.class,
    PortfolioService.class,
    AssetService.class,
    TrnService.class,
    ExchangeService.class
})
@EnableFeignClients(basePackages = "com.beancounter.client")
@ImportAutoConfiguration({
    HttpMessageConvertersAutoConfiguration.class,
    FeignAutoConfiguration.class})
public class ClientConfig {

}
