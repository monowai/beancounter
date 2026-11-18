package com.beancounter.common.model

import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A transaction's cash flow is dated when its cost was paid.
 */
class PeriodicCashFlowsTest {
    private val asset = AssetUtils.getTestAsset(Market("US"), "BRK.B")
    private val recordDate = LocalDate.of(2026, 11, 18)
    private val heldSince = LocalDate.of(2016, 11, 18)

    private fun add(costDate: LocalDate?) =
        Trn(
            trnType = TrnType.ADD,
            asset = asset,
            quantity = BigDecimal.TEN,
            tradeDate = recordDate,
            tradeAmount = BigDecimal("1157.10"),
            costDate = costDate
        )

    @Test
    fun `should date the cash flow at the cost date when there is one`() {
        val flows = PeriodicCashFlows().apply { add(add(heldSince)) }

        assertThat(flows.cashFlows).containsExactly(CashFlow(-1157.10, heldSince))
    }

    @Test
    fun `should date the cash flow at the trade date when there is no cost date`() {
        val flows = PeriodicCashFlows().apply { add(add(null)) }

        assertThat(flows.cashFlows).containsExactly(CashFlow(-1157.10, recordDate))
    }

    @Test
    fun `should carry the cost date through the transaction json`() {
        val json = objectMapper.writeValueAsString(add(heldSince))

        assertThat(json).contains("\"costDate\":\"2016-11-18\"")
        assertThat(objectMapper.readValue(json, Trn::class.java).costDate).isEqualTo(heldSince)
    }
}