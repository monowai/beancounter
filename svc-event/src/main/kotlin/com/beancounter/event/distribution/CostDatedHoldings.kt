package com.beancounter.event.distribution

import com.beancounter.client.services.TrnService
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.event.corporate.CorporateEventStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Shares a portfolio's imported holdings are owed a dividend for. */
data class ImportedShares(
    val portfolio: Portfolio,
    val asset: Asset,
    val quantity: BigDecimal
) {
    /** [held], what svc-position reports on the record date if anything, with these shares added. */
    fun addedTo(held: Position?): Position {
        val position = held ?: Position(asset, portfolio)
        position.quantityValues.purchased = position.quantityValues.purchased.add(quantity)
        return position
    }
}

/**
 * Imported holdings owed a dividend. A screenshot import records an ADD on the statement's
 * date (its trade date) with the approximate date it was bought (its cost date). For dividends
 * only, its quantity counts as held from the cost date: a dividend recorded on R is owed for an
 * ADD when costDate <= R < tradeDate. svc-position cannot see the holding before its trade date,
 * so these shares are added to whatever it reports on R.
 *
 * The statement's quantity is already after every split up to the trade date, so the shares
 * owed on R are the quantity divided by the splits recorded after R up to the trade date.
 * Splits and every other event type are never owed through here.
 */
@Service
class CostDatedHoldings(
    private val trnService: TrnService,
    private val eventStore: CorporateEventStore
) {
    private val log = LoggerFactory.getLogger(CostDatedHoldings::class.java)

    /** The shares owed [event] through imported holdings, by portfolio id. */
    fun owed(event: CorporateEvent): Map<String, ImportedShares> {
        if (event.trnType != TrnType.DIVI) return emptyMap()
        return costDatedAdds(event)
            .filter { owes(it, event.recordDate) }
            .groupBy { it.portfolio.id }
            .mapValues { (_, adds) ->
                ImportedShares(
                    adds.first().portfolio,
                    adds.first().asset,
                    adds.sumOf { it.quantity.divide(splitFactor(event, it.tradeDate), MathContext.DECIMAL64) }
                )
            }
    }

    /**
     * A failed read leaves the imported holdings out rather than failing the event: the
     * portfolios svc-position reports as holders are still paid.
     */
    @Suppress("TooGenericExceptionCaught") // Any client failure is logged; the holders still get the event.
    private fun costDatedAdds(event: CorporateEvent): List<Trn> =
        try {
            trnService.queryCostDated(event.assetId, event.recordDate).data.toTrns()
        } catch (e: Exception) {
            log.warn("Imported holdings not read, asset: {}, recordDate: {}", event.assetId, event.recordDate, e)
            emptyList()
        }

    private fun owes(
        add: Trn,
        recordDate: LocalDate
    ): Boolean {
        val costDate = add.costDate ?: return false
        return add.trnType == TrnType.ADD && costDate <= recordDate && recordDate < add.tradeDate
    }

    // The product of the asset's split ratios recorded after the dividend's record date, up to
    // and including the trade date.
    private fun splitFactor(
        event: CorporateEvent,
        tradeDate: LocalDate
    ): BigDecimal =
        eventStore
            .findInRange(event.assetId, event.recordDate.plusDays(1), tradeDate)
            .filter { it.trnType == TrnType.SPLIT && it.split.signum() > 0 }
            .fold(BigDecimal.ONE) { factor, split -> factor.multiply(split.split) }
}