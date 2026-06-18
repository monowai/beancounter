package com.beancounter.marketdata.trn

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.assets.ContributionFrequency
import com.beancounter.marketdata.assets.PrivateAssetConfig
import com.beancounter.marketdata.assets.PrivateAssetConfigRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Verifies BALANCE snapshots are stamped with the contribution recognised
 * since the prior snapshot, derived from the asset's contribution config.
 */
class BalanceContributionStamperTest {
    private val repository = mock<PrivateAssetConfigRepository>()
    private val stamper = BalanceContributionStamper(repository)

    private val portfolio = Portfolio(id = "P1", code = "P1")
    private val cpf =
        Asset(code = "CPF", id = "cpf-1", market = Market("PRIVATE"))

    private fun balance(
        date: String,
        amount: String
    ) = Trn(
        trnType = TrnType.BALANCE,
        asset = cpf,
        quantity = BigDecimal(amount),
        tradeAmount = BigDecimal(amount),
        tradeDate = LocalDate.parse(date),
        portfolio = portfolio
    )

    @Test
    fun `stamps contribution on later snapshots and leaves the first one null`() {
        whenever(repository.findByAssetIdIn(listOf("cpf-1")))
            .thenReturn(
                listOf(
                    PrivateAssetConfig(
                        assetId = "cpf-1",
                        monthlyContribution = BigDecimal("600"),
                        contributionFrequency = ContributionFrequency.MONTHLY
                    )
                )
            )
        val first = balance("2026-01-15", "100000")
        val second = balance("2026-04-15", "102000") // 3 months later

        stamper.stamp(listOf(first, second))

        assertThat(first.contribution).isNull()
        // 3 months * 600 = 1,800 of the 2,000 increase is contribution.
        assertThat(second.contribution).isEqualByComparingTo(BigDecimal("1800"))
    }

    @Test
    fun `annual frequency is converted to a monthly equivalent`() {
        whenever(repository.findByAssetIdIn(listOf("cpf-1")))
            .thenReturn(
                listOf(
                    PrivateAssetConfig(
                        assetId = "cpf-1",
                        monthlyContribution = BigDecimal("12000"),
                        contributionFrequency = ContributionFrequency.ANNUAL
                    )
                )
            )
        val first = balance("2026-01-10", "50000")
        val second = balance("2026-07-10", "55000") // 6 months later

        stamper.stamp(listOf(first, second))

        // 12,000/yr -> 1,000/mo * 6 months = 6,000.
        assertThat(second.contribution).isEqualByComparingTo(BigDecimal("6000"))
    }

    @Test
    fun `no config means no contribution is stamped`() {
        whenever(repository.findByAssetIdIn(listOf("cpf-1"))).thenReturn(emptyList())
        val first = balance("2026-01-15", "100000")
        val second = balance("2026-04-15", "102000")

        stamper.stamp(listOf(first, second))

        assertThat(first.contribution).isNull()
        assertThat(second.contribution).isNull()
    }
}