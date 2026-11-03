package com.beancounter.position.networth

import com.beancounter.auth.TokenService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
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
 * stored closes a week before in one price request.
 */
@Component
class WeekChange(
    private val priceService: PriceService,
    private val tokenService: TokenService,
    private val dateUtils: DateUtils
) {
    private val cashUtils = CashUtils()

    /**
     * Each priced, non-cash holding's PORTFOLIO value now less that value at its close 7 days
     * before: value × (1 − then ÷ now), both closes in the asset's own currency. A holding with
     * no close then (listed since, or never priced) moves nothing. Null when unknown: the read
     * failed, or no holding came back priced. A secondary figure never fails the headline.
     */
    fun of(
        positions: Positions,
        asAt: String
    ): BigDecimal? {
        // Value now in PORTFOLIO terms and close now in the asset's own currency, read once.
        val priced =
            positions.positions.values.mapNotNull { position ->
                if (cashUtils.isCash(position.asset)) return@mapNotNull null
                val valueNow = position.moneyValues[Position.In.PORTFOLIO]?.marketValue
                val closeNow = position.moneyValues[Position.In.TRADE]?.priceData?.close
                if (valueNow == null || closeNow == null || closeNow.signum() <= 0) return@mapNotNull null
                PricedHolding(position.asset, valueNow, closeNow)
            }
        if (priced.isEmpty()) return BigDecimal.ZERO
        val weekAgo = dateUtils.getFormattedDate(asAt).minusDays(DAYS_IN_WEEK)
        val then = weekAgoCloses(priced, weekAgo)
        return when {
            then == null -> {
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
                            .multiply(
                                closeThen
                            ).divide(holding.closeNow, RATIO_SCALE, RoundingMode.HALF_UP)
                    acc.add(holding.valueNow.subtract(valueThen))
                }
            }
        }
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
        val valueNow: BigDecimal,
        val closeNow: BigDecimal
    )

    companion object {
        private const val RATIO_SCALE = 8
        private const val DAYS_IN_WEEK = 7L
        private val log = LoggerFactory.getLogger(WeekChange::class.java)
    }
}