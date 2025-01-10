package com.beancounter.marketdata.currency

import com.beancounter.common.model.Currency
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service

@ConfigurationProperties(prefix = "beancounter.currency")
@Service
class CurrencyConfig {
    final var base: String = "USD"
    var values: Collection<Currency> = arrayListOf()
    var baseCurrency: Currency = Currency(base)
}