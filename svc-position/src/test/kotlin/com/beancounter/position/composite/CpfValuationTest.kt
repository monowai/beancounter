package com.beancounter.position.composite

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.position.Constants.Companion.SGD
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.MapEntry.entry
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class CpfValuationTest {
    private val valuation = CpfValuation()
    private val asAt = LocalDate.of(2026, 6, 7)
    private val cpfAsset =
        Asset(
            code = "CPF",
            id = "cpf-asset-id",
            name = "CPF",
            market = Market("PRIVATE")
        )

    @Test
    fun `supports returns true when policyType is CPF`() {
        val config = config(policyType = "CPF")
        assertThat(valuation.supports(config)).isTrue
    }

    @Test
    fun `supports returns false when policyType is null`() {
        val config = config(policyType = null)
        assertThat(valuation.supports(config)).isFalse
    }

    @Test
    fun `supports returns false when policyType is not CPF`() {
        val config = config(policyType = "ILP")
        assertThat(valuation.supports(config)).isFalse
    }

    @Test
    fun `value rolls up sub-account balances into position`() {
        val config =
            config(
                policyType = "CPF",
                subAccounts =
                    listOf(
                        sub("OA", "145000"),
                        sub("SA", "78000"),
                        sub("MA", "58000", liquid = false),
                        sub("RA", "0")
                    )
            )

        val position = valuation.value(cpfAsset, config, asAt)

        assertThat(position.asset).isEqualTo(cpfAsset)
        assertThat(position.subAccounts).containsOnly(
            entry("OA", BigDecimal("145000")),
            entry("SA", BigDecimal("78000")),
            entry("MA", BigDecimal("58000")),
            entry("RA", BigDecimal("0"))
        )
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal("281000"))
    }

    @Test
    fun `value returns zero quantity when sub-accounts empty`() {
        val config = config(policyType = "CPF", subAccounts = emptyList())

        val position = valuation.value(cpfAsset, config, asAt)

        assertThat(position.subAccounts).isEmpty()
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal.ZERO)
    }

    private fun config(
        policyType: String?,
        subAccounts: List<SubAccountDto> = emptyList()
    ) = PrivateAssetConfigDto(
        assetId = cpfAsset.id,
        policyType = policyType,
        currency = SGD.code,
        subAccounts = subAccounts
    )

    private fun sub(
        code: String,
        balance: String,
        liquid: Boolean = true
    ) = SubAccountDto(
        code = code,
        balance = BigDecimal(balance),
        liquid = liquid
    )
}