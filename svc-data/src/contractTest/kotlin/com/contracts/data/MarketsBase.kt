package com.contracts.data

import com.beancounter.marketdata.trn.cash.CashBalancesBean
import org.springframework.boot.test.mock.mockito.MockBean

/**
 * Base class for Market Contract tests
 */
class MarketsBase : ContractVerifierBase() {
    @MockBean
    internal lateinit var cashBalancesBean: CashBalancesBean
}
