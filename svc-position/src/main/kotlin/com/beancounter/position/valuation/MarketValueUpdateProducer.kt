package com.beancounter.position.valuation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.setMarketValue
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.messaging.MessagingException
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Publishes a portfolio's market value to bc-data after a valuation, which stores
 * it on the portfolio.
 */
@Service
class MarketValueUpdateProducer(
    private val streamBridge: StreamBridge,
    private val dateUtils: DateUtils
) {
    private val log = LoggerFactory.getLogger(MarketValueUpdateProducer::class.java)

    /**
     * Publishes [valued]'s market value when it is today's valuation; a historical
     * one is never published. Only a single portfolio's closing valuation reaches
     * here: an aggregate belongs to no portfolio, and a live (delayed-quote)
     * valuation is display only.
     *
     * Market value is sent in portfolio.base currency; the frontend converts for display.
     */
    fun publish(valued: Positions) {
        if (!dateUtils.isToday(valued.asAt)) return
        // Best effort, like the send: a payload that cannot be built never fails the valuation.
        runCatching { payload(valued) }
            .onSuccess(::sendMessage)
            .onFailure { log.error("Market value update for portfolio {} not built", valued.portfolio.code, it) }
    }

    /** [valued]'s market value as the portfolio update bc-data stores, in portfolio.base. */
    private fun payload(valued: Positions): Portfolio {
        val portfolio = valued.portfolio
        val baseTotals = valued.totals[Position.In.BASE]
        val marketValue = baseTotals?.marketValue ?: BigDecimal.ZERO
        val irr = baseTotals?.irr ?: BigDecimal.ZERO
        val gainOnDay = valued.gainOnDay(Position.In.BASE)
        val valuedAt = dateUtils.getDate(valued.asAt)

        log.trace(
            "Sending MV update: portfolio={}, base={}, marketValue={}, gainOnDay={}, valuedAt={}",
            portfolio.code,
            portfolio.base.code,
            marketValue,
            gainOnDay,
            valuedAt
        )
        return portfolio.setMarketValue(marketValue, irr, gainOnDay, assetClassification(valued), valuedAt)
    }

    fun sendMessage(payload: Portfolio) {
        try {
            val message =
                MessageBuilder
                    .withPayload(payload)
                    .build()
            if (!streamBridge.send("portfolioMarketValue-out-0", message)) {
                log.error("StreamBridge returned false for portfolio market value update: {}", payload.code)
            }
        } catch (e: MessagingException) {
            log.error("Failed to send market value update for portfolio {}: {}", payload.code, e.message)
        }
    }

    /** BASE market value summed by each position's effectiveReportCategory. */
    private fun assetClassification(positions: Positions): Map<String, BigDecimal> =
        positions.positions.values
            .groupBy { it.asset.effectiveReportCategory }
            .mapValues { (_, positionsInCategory) ->
                positionsInCategory
                    .mapNotNull { it.moneyValues[Position.In.BASE]?.marketValue }
                    .fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
            }
}