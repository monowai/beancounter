package com.contracts.data

import com.beancounter.marketdata.trn.cash.CashBalancesBean
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Base class for Market Contract tests
 */
class MarketsBase : ContractVerifierBase() {
    @MockitoBean
    internal lateinit var cashBalancesBean: CashBalancesBean
}