package com.beancounter.marketdata.assets.figi

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(FigiProxy::class)
class FigiConfig {
    @Value("\${beancounter.market.providers.FIGI.key:demo}")
    var apiKey: String? = null

    @Value("\${beancounter.market.providers.FIGI.enabled:true}")
    var enabled: Boolean? = null
}
