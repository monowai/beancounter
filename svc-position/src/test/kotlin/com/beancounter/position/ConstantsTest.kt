package com.beancounter.position

import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.nzdCashBalance
import com.beancounter.position.Constants.Companion.usdCashBalance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The shared cash fixtures hold the currency they are named for. */
class ConstantsTest {
    @Test
    fun `should hold an NZD cash balance in NZD`() {
        assertThat(nzdCashBalance.market.currency).isEqualTo(NZD)
    }

    @Test
    fun `should hold a USD cash balance in USD`() {
        assertThat(usdCashBalance.market.currency).isEqualTo(USD)
    }
}