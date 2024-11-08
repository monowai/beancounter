package com.beancounter.client.sharesight

import com.beancounter.client.ingest.TrnAdapter
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Factory for getting the appropriate row transformer.
 *
 * @author mikeh
 * @since 2019-03-10
 */
@Service
class ShareSightFactory(
    val shareSightDivi: ShareSightDividendAdapter,
    val shareSightTrade: ShareSightTradeAdapter,
) {
    private val tradeTypes = setOf("BUY", "SELL", "SPLIT")

    /**
     * Figure out if we're dealing with a Trade or Dividend row.
     *
     * @param row analyze this
     * @return appropriate transformer
     */
    fun adapter(row: List<String>): TrnAdapter =
        if (tradeTypes.contains(row[ShareSightTradeAdapter.TYPE].uppercase(Locale.getDefault()))) {
            shareSightTrade
        } else {
            shareSightDivi
        }
}
