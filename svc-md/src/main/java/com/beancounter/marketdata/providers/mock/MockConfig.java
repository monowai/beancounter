package com.beancounter.marketdata.providers.mock;

import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Data
@Configuration
@Import({MockProviderService.class})
public class MockConfig  {
}
