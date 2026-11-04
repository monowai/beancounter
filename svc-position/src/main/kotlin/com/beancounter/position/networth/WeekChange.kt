package com.beancounter.position.networth

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * How much the current holdings moved over the last 7 days, read from their
 * stored closes and FX rates a week before: one price request and, for foreign
 * holdings, one FX request.
 */
@Component
class WeekChange(
    private val priceService: PriceService,
    private val fxService: FxService,
    private val tokenService: TokenService,
    private val dateUtils: DateUtils
) {
    private val cashUtils = CashUtils()

    /**
     * Each priced, non-cash holding's PORTFOLIO value now less that value a week before: the
     * week-ago close in the asset's own currency, converted at the week-ago FX rate, against
     * today's close in PORTFOLIO terms. The figure carries the week's FX move as well as the
     * price move. A holding with no close then (listed since, or never priced) moves nothing.
     * Null when unknown: [asAt] is not a date, a read failed, or no holding came back priced.
     * A secondary figure never fails the headline.
     */
    fun of(
        positions: Positions,
        asAt: String
    ): BigDecimal? {
        val priced = positions.positions.values.mapNotNull(::pricedHolding)
        if (priced.isEmpty()) return null
        return weekAgo(asAt)?.let { change(priced, it) }
    }

    /** [asAt] less a week; null, logged, when [asAt] is not a date. */
    private fun weekAgo(asAt: String): LocalDate? =
        runCatching { dateUtils.getFormattedDate(asAt).minusDays(DAYS_IN_WEEK) }
            .onFailure { log.warn("7-day change unknown: asAt {} is not a date: {}", asAt, it.message) }
            .getOrNull()

    private fun change(
        priced: List<PricedHolding>,
        weekAgo: LocalDate
    ): BigDecimal? {
        val then = weekAgoCloses(priced, weekAgo)
        val rates = weekAgoRates(priced, weekAgo)
        return when {
            then == null || rates == null -> {
                null
            }
            then.isEmpty() -> {
                log.debug("7-day change unknown: no week-ago close for {} holdings at {}", priced.size, weekAgo)
                null
            }
            else -> {
                priced.fold(BigDecimal.ZERO) { acc, holding ->
                    val closeThen = then[holding.asset.id] ?: return@fold acc
                    val valueThen =
                        holding.valueNow
                            .multiply(closeThen.multiply(rates.getValue(holding.pair)))
                            .divide(holding.closeNow, RATIO_SCALE, RoundingMode.HALF_UP)
                    acc.add(holding.valueNow.subtract(valueThen))
                }
            }
        }
    }

    /**
     * A non-cash holding's value and close now, both in PORTFOLIO terms; null when unpriced.
     * The trade close is read only to name the currency pair its price is quoted in.
     */
    private fun pricedHolding(position: Position): PricedHolding? {
        if (cashUtils.isCash(position.asset)) return null
        val portfolio = position.moneyValues[Position.In.PORTFOLIO] ?: return null
        val trade = position.moneyValues[Position.In.TRADE] ?: return null
        val closeNow = portfolio.priceData.close
        if (closeNow.signum() <= 0 || trade.priceData.close.signum() <= 0) return null
        return PricedHolding(
            position.asset,
            IsoCurrencyPair(trade.currency.code, portfolio.currency.code),
            portfolio.marketValue,
            closeNow
        )
    }

    /**
     * Rates from each holding's own currency into PORTFOLIO on [weekAgo], keyed by pair, the
     * same-currency pair at 1, in one FX request. Null when the rates cannot be read or one
     * is missing: the change is then unknown rather than computed at a made-up rate.
     */
    private fun weekAgoRates(
        priced: List<PricedHolding>,
        weekAgo: LocalDate
    ): Map<IsoCurrencyPair, BigDecimal>? {
        val pairs = priced.map { it.pair }.toSet()
        val foreign = pairs.filter { it.from != it.to }
        val read = if (foreign.isEmpty()) emptyMap() else readRates(foreign, weekAgo)
        val missing = read?.let { rates -> foreign.filter { rates[it]?.rate == null } }.orEmpty()
        return when {
            read == null -> {
                null
            }
            missing.isNotEmpty() -> {
                log.warn("7-day change unknown: no FX rate for {} at {}", missing, weekAgo)
                null
            }
            else -> {
                pairs.associateWith { pair -> if (pair.from == pair.to) BigDecimal.ONE else read.getValue(pair).rate }
            }
        }
    }

    /** [pairs] read in one FX request on [weekAgo]; null, logged, when the read fails. */
    private fun readRates(
        pairs: List<IsoCurrencyPair>,
        weekAgo: LocalDate
    ): Map<IsoCurrencyPair, FxRate>? {
        val request = FxRequest(rateDate = weekAgo.toString())
        pairs.forEach { request.add(it) }
        return runCatching { fxService.getRates(request, tokenService.bearerToken).data.rates }
            .onFailure { log.warn("7-day change unknown: week-ago FX for {} unavailable: {}", weekAgo, it.message) }
            .getOrNull()
    }

    /** Positive closes by asset id on [weekAgo]; null when the prices cannot be read. */
    private fun weekAgoCloses(
        priced: List<PricedHolding>,
        weekAgo: LocalDate
    ): Map<String, BigDecimal>? {
        val request =
            PriceRequest(
                date = weekAgo.toString(),
                assets = priced.map { PriceAsset(it.asset.market.code, it.asset.code, assetId = it.asset.id) },
                currentMode = false
            )
        return try {
            priceService
                .getPrices(request, tokenService.bearerToken)
                .data
                .filter { it.close.signum() > 0 }
                .associate { it.asset.id to it.close }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: RuntimeException
        ) {
            log.warn("7-day change unknown: week-ago prices for {} unavailable: {}", weekAgo, e.message)
            null
        }
    }

    private class PricedHolding(
        val asset: Asset,
        val pair: IsoCurrencyPair,
        val valueNow: BigDecimal,
        val closeNow: BigDecimal
    )

    companion object {
        private const val RATIO_SCALE = 8
        private const val DAYS_IN_WEEK = 7L
        private val log = LoggerFactory.getLogger(WeekChange::class.java)
    }
}