package com.beancounter.marketdata.providers.mock

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Deprecate this. Mock config for providing market data.
 */
@Configuration
@Import(MockProviderService::class)
class MockConfig
