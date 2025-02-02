package com.beancounter.marketdata.config

import com.beancounter.marketdata.cash.CashService
import com.beancounter.marketdata.currency.CurrencyService
import jakarta.transaction.Transactional
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
@Transactional
class StaticConfigService(
    val currencyService: CurrencyService,
    val cashService: CashService
) {
    @EventListener(ContextRefreshedEvent::class)
    fun onApplicationEvent(event: ContextRefreshedEvent) {
        currencyService.persist()
        cashService.createCashBalanceAssets()
    }
}