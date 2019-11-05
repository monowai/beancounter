package com.beancounter.marketdata.providers.wtd;

import com.beancounter.marketdata.config.StaticConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({StaticConfig.class, WtdService.class, WtdRequester.class})
public class WtdConfig {
}
