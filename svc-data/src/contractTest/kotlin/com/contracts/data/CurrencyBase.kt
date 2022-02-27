package com.contracts.data

import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.beans.factory.annotation.Autowired

/**
 * Currency Contract Tests.
 */

class CurrencyBase : ContractVerifierBase() {
    @Autowired
    private lateinit var currencyService: CurrencyService
}
