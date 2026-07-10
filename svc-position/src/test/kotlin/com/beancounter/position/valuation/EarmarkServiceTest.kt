package com.beancounter.position.valuation

import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants.Companion.NASDAQ
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Verifies EarmarkService stamps the signed net PROPOSED cash-leg quantity onto
 * each cash position, keyed the same way positions are keyed, and never touches
 * non-cash positions.
 */
@ExtendWith(MockitoExtension::class)
class EarmarkServiceTest {
    @Mock
    private lateinit var trnService: TrnService

    private val portfolio: Portfolio = getPortfolio()

    private fun cash(code: String): Asset =
        getTestAsset(NASDAQ, code).apply {
            assetCategory = AssetCategory(AssetCategory.CASH, "Cash")
        }

    private fun leg(
        asset: Asset,
        type: TrnType,
        qty: BigDecimal
    ): Trn =
        Trn(
            trnType = type,
            asset = asset,
            quantity = qty,
            portfolio = portfolio
        )

    private fun service() = EarmarkService(trnService)

    @Test
    fun `stamps signed net of deposit and withdrawal on the cash asset`() {
        val usd = cash("USD")
        val positions = Positions(portfolio)
        val position = positions.getOrCreate(usd)

        whenever(trnService.queryProposedCash(eq(portfolio.id), any())).thenReturn(
            TrnResponse(
                listOf(
                    leg(usd, TrnType.DEPOSIT, BigDecimal("300")),
                    leg(usd, TrnType.WITHDRAWAL, BigDecimal("100"))
                )
            )
        )

        service().stamp(portfolio, positions, "today")

        assertThat(position.earmarkedQuantity)
            .describedAs("DEPOSIT 300 - WITHDRAWAL 100 = net +200")
            .isEqualByComparingTo(BigDecimal("200"))
    }

    @Test
    fun `stamps each cash asset independently`() {
        val usd = cash("USD")
        val sgd = cash("SGD")
        val positions = Positions(portfolio)
        val usdPos = positions.getOrCreate(usd)
        val sgdPos = positions.getOrCreate(sgd)

        whenever(trnService.queryProposedCash(eq(portfolio.id), any())).thenReturn(
            TrnResponse(
                listOf(
                    leg(usd, TrnType.DEPOSIT, BigDecimal("500")),
                    leg(sgd, TrnType.WITHDRAWAL, BigDecimal("250"))
                )
            )
        )

        service().stamp(portfolio, positions, "today")

        assertThat(usdPos.earmarkedQuantity).isEqualByComparingTo(BigDecimal("500"))
        assertThat(sgdPos.earmarkedQuantity).isEqualByComparingTo(BigDecimal("-250"))
    }

    @Test
    fun `never stamps a non-cash position`() {
        val usd = cash("USD")
        val equity = getTestAsset(NASDAQ, "ABC") // default category Equity -> not cash
        val positions = Positions(portfolio)
        val cashPos = positions.getOrCreate(usd)
        val equityPos = positions.getOrCreate(equity)

        whenever(trnService.queryProposedCash(eq(portfolio.id), any())).thenReturn(
            TrnResponse(listOf(leg(usd, TrnType.DEPOSIT, BigDecimal("400"))))
        )

        service().stamp(portfolio, positions, "today")

        assertThat(cashPos.earmarkedQuantity).isEqualByComparingTo(BigDecimal("400"))
        assertThat(equityPos.earmarkedQuantity)
            .describedAs("equity position is never earmarked")
            .isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `no-op when there are no proposed legs`() {
        val usd = cash("USD")
        val positions = Positions(portfolio)
        val position = positions.getOrCreate(usd)

        whenever(trnService.queryProposedCash(eq(portfolio.id), any()))
            .thenReturn(TrnResponse(emptyList()))

        service().stamp(portfolio, positions, "today")

        assertThat(position.earmarkedQuantity).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `leg for an asset without a built position is ignored`() {
        val usd = cash("USD")
        val eur = cash("EUR") // proposed leg exists but no position built for it
        val positions = Positions(portfolio)
        val usdPos = positions.getOrCreate(usd)

        whenever(trnService.queryProposedCash(eq(portfolio.id), any())).thenReturn(
            TrnResponse(
                listOf(
                    leg(usd, TrnType.DEPOSIT, BigDecimal("100")),
                    leg(eur, TrnType.DEPOSIT, BigDecimal("999"))
                )
            )
        )

        service().stamp(portfolio, positions, "today")

        assertThat(usdPos.earmarkedQuantity).isEqualByComparingTo(BigDecimal("100"))
        assertThat(positions.positions).hasSize(1) // EUR leg did not create a phantom position
    }
}