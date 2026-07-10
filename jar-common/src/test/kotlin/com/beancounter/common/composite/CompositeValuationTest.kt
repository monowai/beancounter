package com.beancounter.common.composite

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.MapEntry.entry
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * jar-common is the single source of truth for composite (CPF / ILP / generic)
 * balance roll-ups. Both svc-position (synthesising a Position for the
 * /positions/composite endpoint) and svc-retire (adjusting Plan total / liquid
 * assets) call the same class so neither re-implements the math.
 */
class CompositeValuationTest {
    private val valuation = CompositeValuation()

    @Test
    fun `returns null when policy type is null`() {
        val result =
            valuation.value(
                policyType = null,
                subAccounts =
                    listOf(SubAccountBalance(code = "OA", balance = BigDecimal("145000")))
            )

        assertThat(result).isNull()
    }

    @Test
    fun `returns null when policy type is unknown`() {
        val result =
            valuation.value(
                policyType = "UNHANDLED",
                subAccounts = listOf(SubAccountBalance(code = "X", balance = BigDecimal.ONE))
            )

        assertThat(result).isNull()
    }

    @Test
    fun `CPF rolls up total liquid and non-liquid`() {
        val result =
            valuation.value(
                policyType = "CPF",
                subAccounts =
                    listOf(
                        SubAccountBalance(code = "OA", balance = BigDecimal("145000"), liquid = true),
                        SubAccountBalance(code = "SA", balance = BigDecimal("78000"), liquid = true),
                        SubAccountBalance(code = "MA", balance = BigDecimal("58000"), liquid = false),
                        SubAccountBalance(code = "RA", balance = BigDecimal("0"), liquid = true)
                    )
            )

        assertThat(result).isNotNull
        assertThat(result!!.total).isEqualByComparingTo(BigDecimal("281000"))
        assertThat(result.liquid).isEqualByComparingTo(BigDecimal("223000"))
        assertThat(result.nonLiquid).isEqualByComparingTo(BigDecimal("58000"))
        assertThat(result.byCode).containsOnly(
            entry("OA", BigDecimal("145000")),
            entry("SA", BigDecimal("78000")),
            entry("MA", BigDecimal("58000")),
            entry("RA", BigDecimal("0"))
        )
        assertThat(result.byCode.keys).containsExactly("OA", "SA", "MA", "RA")
    }

    @Test
    fun `duplicate sub-account codes accumulate in byCode`() {
        val result =
            valuation.value(
                policyType = "CPF",
                subAccounts =
                    listOf(
                        SubAccountBalance(code = "OA", balance = BigDecimal("100"), liquid = true),
                        SubAccountBalance(code = "OA", balance = BigDecimal("50"), liquid = true)
                    )
            )

        assertThat(result).isNotNull
        assertThat(result!!.total).isEqualByComparingTo(BigDecimal("150"))
        assertThat(result.liquid).isEqualByComparingTo(BigDecimal("150"))
        assertThat(result.byCode).containsOnly(entry("OA", BigDecimal("150")))
    }

    @Test
    fun `US_401K rolls up sub-account balances`() {
        val result =
            valuation.value(
                policyType = "US_401K",
                subAccounts =
                    listOf(
                        SubAccountBalance(code = "TRADITIONAL", balance = BigDecimal("120000"), liquid = true),
                        SubAccountBalance(code = "ROTH", balance = BigDecimal("30000"), liquid = true)
                    )
            )

        assertThat(result).isNotNull
        assertThat(result!!.total).isEqualByComparingTo(BigDecimal("150000"))
        assertThat(result.liquid).isEqualByComparingTo(BigDecimal("150000"))
        assertThat(result.byCode).containsOnly(
            entry("TRADITIONAL", BigDecimal("120000")),
            entry("ROTH", BigDecimal("30000"))
        )
    }

    @Test
    fun `US_IRA and UK_ISA are supported policy types`() {
        val ira =
            valuation.value(
                policyType = "US_IRA",
                subAccounts = listOf(SubAccountBalance(code = "IRA", balance = BigDecimal("55000")))
            )
        val isa =
            valuation.value(
                policyType = "UK_ISA",
                subAccounts = listOf(SubAccountBalance(code = "ISA", balance = BigDecimal("20000")))
            )

        assertThat(ira).isNotNull
        assertThat(ira!!.total).isEqualByComparingTo(BigDecimal("55000"))
        assertThat(isa).isNotNull
        assertThat(isa!!.total).isEqualByComparingTo(BigDecimal("20000"))
    }

    @Test
    fun `CPF with empty sub-accounts is zero`() {
        val result = valuation.value(policyType = "CPF", subAccounts = emptyList())

        assertThat(result).isNotNull
        assertThat(result!!.total).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.liquid).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.nonLiquid).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.byCode).isEmpty()
    }
}