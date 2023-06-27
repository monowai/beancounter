package com.contracts.data

import com.beancounter.marketdata.trn.cash.CashBalancesBean
import org.springframework.boot.test.mock.mockito.MockBean

/**
 * Currency Contract Tests.
 */

class CurrencyBase : ContractVerifierBase() {
    @MockBean
    internal lateinit var cashBalancesBean: CashBalancesBean
}
