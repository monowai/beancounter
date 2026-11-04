package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.MathUtils.Companion.HUNDRED
import com.beancounter.marketdata.price.DayChange
import com.beancounter.marketdata.providers.eodhd.EodhdPriceService.Companion.ID
import com.beancounter.marketdata.providers.eodhd.model.EodhdQuote
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Live (delayed) EODHD quotes for display. Never persisted.
 */
@Component
class EodhdQuotes(
    private val eodhdConfig: EodhdConfig,
    private val eodhdProxy: EodhdProxy
) {
    private val log = LoggerFactory.getLogger(EodhdQuotes::class.java)

    /**
     * Live (delayed) quote from `/api/real-time`. Any failure — plan without live access
     * (403), unknown ticker, rate limit, or "NA" values outside trading history — yields
     * `null` so the caller falls back to the stored close.
     */
    fun getQuote(asset: Asset): MarketData? {
        val symbol = eodhdConfig.getPriceCode(asset)
        val quote =
            try {
                eodhdProxy.getRealTime(symbol, eodhdConfig.apiKey)
            } catch (e: RestClientException) {
                log.debug("EODHD live quote unavailable for {}: {}", symbol, e.message)
                null
            } catch (_: RequestNotPermitted) {
                log.debug("EODHD live quote rate-limited for {}", symbol)
                null
            } ?: return null
        return toQuote(asset, quote)
    }

    /**
     * Live quotes for several assets, [QUOTE_BATCH] symbols per `/api/real-time` request. A
     * failed batch quotes none of its assets; they fall back to the stored close.
     */
    fun getQuotes(assets: Collection<Asset>): Map<String, MarketData> =
        // Assets can share a symbol (an explicit priceSymbol); each one gets the quote.
        assets
            .groupBy { eodhdConfig.getPriceCode(it).uppercase() }
            .entries
            .chunked(QUOTE_BATCH)
            .flatMap { batch ->
                val bySymbol = batch.associate { it.key to it.value }
                val symbols = bySymbol.keys.toList()
                if (symbols.size == 1) {
                    val shared = bySymbol.getValue(symbols.single())
                    val quote = getQuote(shared.first()) ?: return@flatMap emptyList()
                    return@flatMap shared.map { it.id to quote.copy(asset = it) }
                }
                val quotes =
                    try {
                        eodhdProxy.getRealTimeBatch(
                            symbols.first(),
                            symbols.drop(1).joinToString(","),
                            eodhdConfig.apiKey
                        )
                    } catch (e: RestClientException) {
                        log.debug("EODHD live quotes unavailable for {}: {}", symbols, e.message)
                        emptyList()
                    } catch (_: RequestNotPermitted) {
                        log.debug("EODHD live quotes rate-limited for {}", symbols)
                        emptyList()
                    }
                quotes.flatMap { quote ->
                    val shared =
                        quote.code
                            ?.uppercase()
                            ?.let(bySymbol::get)
                            .orEmpty()
                    shared.mapNotNull { asset -> toQuote(asset, quote)?.let { asset.id to it } }
                }
            }.toMap()

    private fun toQuote(
        asset: Asset,
        quote: EodhdQuote
    ): MarketData? {
        val close = quote.close.asDecimal() ?: return null
        if (close.signum() <= 0) return null
        val timestamp = quote.timestamp.asDecimal()?.toLong() ?: return null
        val previousClose = quote.previousClose.asDecimal() ?: BigDecimal.ZERO
        return MarketData(
            asset = asset,
            priceDate =
                Instant
                    .ofEpochSecond(timestamp)
                    .atZone(asset.market.timezone.toZoneId())
                    .toLocalDate(),
            close = close,
            open = quote.open.asDecimal() ?: BigDecimal.ZERO,
            high = quote.high.asDecimal() ?: BigDecimal.ZERO,
            low = quote.low.asDecimal() ?: BigDecimal.ZERO,
            previousClose = previousClose,
            change = quote.change.asDecimal() ?: BigDecimal.ZERO,
            // EODHD's change_p is a percentage; MarketData.changePercent is a fraction.
            changePercent =
                quote.changePercent
                    .asDecimal()
                    ?.divide(HUNDRED, DayChange.CHANGE_PERCENT_SCALE, RoundingMode.HALF_UP)
                    ?: BigDecimal.ZERO,
            // MarketData.volume is Int; clamp as EodhdAdapter does rather than wrap.
            volume =
                quote.volume
                    .asDecimal()
                    ?.toLong()
                    ?.coerceIn(0L, Int.MAX_VALUE.toLong())
                    ?.toInt() ?: 0,
            source = ID
        )
    }

    private fun Any?.asDecimal(): BigDecimal? = this?.toString()?.toBigDecimalOrNull()

    private companion object {
        // EODHD's guidance for one multi-symbol `/api/real-time` request.
        const val QUOTE_BATCH = 15
    }
}