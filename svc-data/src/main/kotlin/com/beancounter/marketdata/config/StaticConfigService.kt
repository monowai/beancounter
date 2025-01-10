package com.beancounter.marketdata.config

import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.trn.cash.CashBalancesBean
import jakarta.transaction.Transactional
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
@Transactional
class StaticConfigService(
    val currencyService: CurrencyService,
    val cashBalancesBean: CashBalancesBean
) {
    @EventListener(ContextRefreshedEvent::class)
    fun onApplicationEvent(event: ContextRefreshedEvent) {
        currencyService.persist()
        cashBalancesBean.createCashBalanceAssets()
    }
}