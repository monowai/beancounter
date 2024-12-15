package com.contracts.data

import com.beancounter.marketdata.trn.cash.CashBalancesBean
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Currency Contract Tests.
 */

class CurrencyBase : ContractVerifierBase() {
    @MockitoBean
    internal lateinit var cashBalancesBean: CashBalancesBean
}