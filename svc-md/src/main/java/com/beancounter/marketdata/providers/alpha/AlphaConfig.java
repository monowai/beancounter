package com.beancounter.marketdata.providers.alpha;

import com.beancounter.marketdata.config.StaticConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({StaticConfig.class, AlphaRequester.class, AlphaService.class})
public class AlphaConfig {
}
