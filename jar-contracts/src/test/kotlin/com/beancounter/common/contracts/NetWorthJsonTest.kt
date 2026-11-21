package com.beancounter.common.contracts

import com.beancounter.common.model.DayFlows
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * The Net Worth payload carries the day's external cash flows; positions carry them
 * internally only.
 */
class NetWorthJsonTest {
    @Test
    fun `should round trip the day's cash flows`() {
        val netWorth =
            NetWorth(
                asAt = "2024-01-15",
                currency = "SGD",
                flowsInOnDay = BigDecimal("100.00"),
                flowsOutOnDay = BigDecimal("-30.56"),
                flowsOnDay = BigDecimal("69.44")
            )

        val json = objectMapper.writeValueAsString(netWorth)
        val tree = objectMapper.readTree(json)

        assertThat(tree.get("flowsInOnDay").decimalValue()).isEqualByComparingTo("100.00")
        assertThat(tree.get("flowsOutOnDay").decimalValue()).isEqualByComparingTo("-30.56")
        assertThat(tree.get("flowsOnDay").decimalValue()).isEqualByComparingTo("69.44")
        assertThat(objectMapper.readValue(json, NetWorth::class.java)).isEqualTo(netWorth)
    }

    @Test
    fun `should default the day's cash flows to zero when absent`() {
        val netWorth = objectMapper.readValue("""{"asAt":"2024-01-15","currency":"SGD"}""", NetWorth::class.java)

        assertThat(netWorth.flowsInOnDay).isEqualByComparingTo("0")
        assertThat(netWorth.flowsOutOnDay).isEqualByComparingTo("0")
        assertThat(netWorth.flowsOnDay).isEqualByComparingTo("0")
    }

    @Test
    fun `should keep the day's cash flows out of serialised positions`() {
        val positions = Positions().apply { flowsOnDay = DayFlows(BigDecimal.TEN, BigDecimal.ONE.negate()) }

        val tree = objectMapper.readTree(objectMapper.writeValueAsString(positions))

        assertThat(tree.has("flowsOnDay")).isFalse()
    }
}