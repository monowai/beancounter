package com.beancounter.event.backfill

import com.beancounter.client.services.PriceService
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.model.TrnType
import com.beancounter.event.behaviour.alpha.AlphaEventAdapter
import com.beancounter.event.corporate.CorporateEventStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Stores one asset's corporate events from bc-data that fall within [daysToAdd]
 * either side of a date, so a scan catches missed events and pre-loads upcoming ones.
 */
@Service
class AssetEventLoader(
    private val priceService: PriceService,
    private val eventStore: CorporateEventStore,
    @Value($$"${beancounter.events.dividend.days-to-add:$${AlphaEventAdapter.DEFAULT_DAYS_TO_ADD}}")
    private val daysToAdd: Long = AlphaEventAdapter.DEFAULT_DAYS_TO_ADD
) {
    /** Returns the number of events stored. */
    fun load(
        assetId: String,
        date: LocalDate
    ): Int {
        val windowStart = date.minusDays(daysToAdd)
        val windowEnd = date.plusDays(daysToAdd)
        val inWindow =
            priceService.getEvents(assetId).data.filter {
                !it.priceDate.isBefore(windowStart) && !it.priceDate.isAfter(windowEnd)
            }
        inWindow.forEach { priceResponse ->
            eventStore.save(
                CorporateEvent(
                    trnType = if (isSplit(priceResponse)) TrnType.SPLIT else TrnType.DIVI,
                    recordDate = priceResponse.priceDate,
                    assetId = assetId,
                    rate = priceResponse.dividend,
                    split = priceResponse.split
                )
            )
        }
        return inWindow.size
    }
}