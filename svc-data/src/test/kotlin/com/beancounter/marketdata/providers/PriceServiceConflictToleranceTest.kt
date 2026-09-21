package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Reproduces the production `MarketData(source, asset_id, priceDate)` unique-constraint
 * Sentry event via [PriceService.handle]'s real code path, without needing real thread
 * concurrency: two brand-new rows for the same asset/date arrive in the same incoming
 * batch (e.g. a provider glitch, or two overlapping backfills merged upstream before
 * `handle` sees them). `buildCreateSet`'s dedup only filters rows whose date is already
 * *stored*; it does nothing for two new rows racing each other within one batch, so the
 * second row's INSERT collides with the first one's inside the same flush - exactly the
 * failure mode [ConflictTolerantWriter] exists to contain. This is the seam
 * [PriceService.handle] actually has for provoking a same-transaction conflict
 * deterministically; a true cross-transaction race is covered directly by
 * `ConflictTolerantWriterTest`.
 */
@SpringMvcDbTest
class PriceServiceConflictToleranceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var priceService: PriceService

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var marketDataRepo: MarketDataRepo

    @Test
    fun `handle does not fail the whole call when a batch contains a same-date duplicate for an asset`() {
        val asset =
            assetRepository.save(
                Asset(
                    code = "PSC-BATCH-DUP",
                    market = NASDAQ,
                    marketCode = NASDAQ.code
                )
            )
        val date = LocalDate.of(2026, 5, 1)

        // Two brand-new rows, same natural key (source, asset_id, priceDate) - nothing
        // pre-exists in the DB, so buildCreateSet's own dedup-against-stored-rows check
        // lets both through into the same persist chunk.
        val first =
            MarketData(
                asset = asset,
                priceDate = date,
                close = BigDecimal("50.00")
            )
        val duplicateSameDate =
            MarketData(
                asset = asset,
                priceDate = date,
                close = BigDecimal("99.00")
            )

        var result: Iterable<MarketData>? = null
        assertThatCode {
            result = priceService.handle(PriceResponse(listOf(first, duplicateSameDate)))
        }.doesNotThrowAnyException()

        assertThat(result).isNotNull

        // Exactly one row landed - the writer's row-by-row retry persisted the first
        // row and skipped the one that collided with it, instead of the whole call
        // (and both rows) being lost to a 500.
        val stored = marketDataRepo.findByAssetIdAndPriceDate(asset.id, date)
        assertThat(stored).isPresent
        assertThat(stored.get().close).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(marketDataRepo.countByAssetIdAndPriceDate(asset.id, date)).isEqualTo(1L)
    }
}