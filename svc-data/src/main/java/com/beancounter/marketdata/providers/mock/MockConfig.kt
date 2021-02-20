package com.beancounter.marketdata.providers.mock

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(MockProviderService::class)
class MockConfig
