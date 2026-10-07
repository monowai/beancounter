package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A stored series with an internal hole derives `previousClose` for the row after the
 * hole from the row before it, so a one-day change silently spans the whole hole. When
 * the hole is filled, the new rows must chain `previousClose` through each other (EOD
 * providers ship no previousClose), and the stored row after the hole must be relinked
 * to its new predecessor.
 */
@SpringMvcDbTest
class PriceServiceGapFillTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var priceService: PriceService

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var marketDataRepo: MarketDataRepo

    private val beforeHole = LocalDate.of(2026, 9, 10)
    private val afterHole = LocalDate.of(2026, 10, 6)

    @Test
    fun `should chain previousClose through a gap fill and relink the stored row after the hole`() {
        val asset =
            assetRepository.save(
                Asset(
                    code = "PSG-GAP",
                    market = NASDAQ,
                    marketCode = NASDAQ.code
                )
            )
        val stored =
            priceService.handle(
                PriceResponse(
                    listOf(
                        row(asset, beforeHole, "106.33"),
                        row(asset, afterHole, "104.18")
                    )
                )
            )
        assertThat(stored).hasSize(2)
        // Precondition: the hole makes the row after it look like a 2% one-day drop.
        assertThat(marketDataRepo.findByAssetIdAndPriceDate(asset.id, afterHole).get().previousClose)
            .isEqualByComparingTo("106.33")

        priceService.handle(
            PriceResponse(
                listOf(
                    row(asset, beforeHole.plusDays(1), "105.84"),
                    row(asset, beforeHole.plusDays(4), "105.82"),
                    row(asset, afterHole.minusDays(1), "103.98")
                )
            )
        )

        val first = marketDataRepo.findByAssetIdAndPriceDate(asset.id, beforeHole.plusDays(1)).get()
        assertThat(first.previousClose).isEqualByComparingTo("106.33")
        val second = marketDataRepo.findByAssetIdAndPriceDate(asset.id, beforeHole.plusDays(4)).get()
        assertThat(second.previousClose).isEqualByComparingTo("105.84")
        val last = marketDataRepo.findByAssetIdAndPriceDate(asset.id, afterHole.minusDays(1)).get()
        assertThat(last.previousClose).isEqualByComparingTo("105.82")

        val relinked = marketDataRepo.findByAssetIdAndPriceDate(asset.id, afterHole).get()
        assertThat(relinked.previousClose).isEqualByComparingTo("103.98")
        assertThat(relinked.change).isEqualByComparingTo("0.20")
        assertThat(relinked.changePercent).isEqualByComparingTo("0.001923")
        // The row before the hole had no new predecessor and keeps its values.
        val untouched = marketDataRepo.findByAssetIdAndPriceDate(asset.id, beforeHole).get()
        assertThat(untouched.previousClose).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `should relink the stored row that follows the batch window`() {
        val asset =
            assetRepository.save(
                Asset(
                    code = "PSG-TAIL",
                    market = NASDAQ,
                    marketCode = NASDAQ.code
                )
            )
        priceService.handle(
            PriceResponse(
                listOf(
                    row(asset, beforeHole, "106.33"),
                    row(asset, afterHole, "104.18")
                )
            )
        )

        // Fill stops well short of the stored successor: it still gets relinked.
        priceService.handle(PriceResponse(listOf(row(asset, beforeHole.plusDays(1), "105.84"))))

        val relinked = marketDataRepo.findByAssetIdAndPriceDate(asset.id, afterHole).get()
        assertThat(relinked.previousClose).isEqualByComparingTo("105.84")
        assertThat(relinked.change).isEqualByComparingTo("-1.66")
    }

    private fun row(
        asset: Asset,
        date: LocalDate,
        close: String
    ) = MarketData(
        asset = asset,
        priceDate = date,
        close = BigDecimal(close),
        source = "EODHD"
    )
}