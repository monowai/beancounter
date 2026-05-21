package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.providers.eodhd.model.EodhdPrice
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Adapter for converting EODHD price rows into BeanCounter [MarketData].
 *
 * EODHD returns an array of price rows; an empty array means "no price for the asset on the requested
 * date" (e.g. weekend, holiday, delisted, bad symbol). In that case we emit a single zero-close row so
 * downstream callers see the asset rather than dropping it silently — matching the marketstack behaviour.
 */
@Service
class EodhdAdapter {
    private val log = LoggerFactory.getLogger(EodhdAdapter::class.java)

    fun toMarketData(
        asset: Asset,
        priceDate: LocalDate,
        rows: List<EodhdPrice>
    ): List<MarketData> {
        if (rows.isEmpty()) {
            log.trace("{} - no EODHD rows for {}", asset.code, priceDate)
            return listOf(zeroPrice(asset, priceDate))
        }
        return rows.map { row ->
            // EODHD ships both `close` (raw) and `adjusted_close` (split- and dividend-adjusted).
            // Use the adjusted figure so historical series stay comparable across splits — raw
            // close would leave pre-split prices N× higher than current quotes and break TWR.
            MarketData(
                asset = asset,
                priceDate = row.date,
                open = row.open,
                close = row.adjustedClose,
                source = EodhdPriceService.ID
            ).also {
                it.high = row.high
                it.low = row.low
                // MarketData.volume is Int; EODHD ships Long. Clamp so high-volume tickers
                // (e.g. heavy ETF flow days) don't silently overflow to a negative Int.
                it.volume = row.volume.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            }
        }
    }

    private fun zeroPrice(
        asset: Asset,
        priceDate: LocalDate
    ): MarketData {
        val md =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                source = EodhdPriceService.ID
            )
        md.close = BigDecimal.ZERO
        return md
    }
}