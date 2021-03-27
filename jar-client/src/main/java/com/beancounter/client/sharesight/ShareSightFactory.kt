package com.beancounter.client.sharesight

import com.beancounter.client.ingest.TrnAdapter
import org.springframework.stereotype.Service

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

    /**
     * Figure out if we're dealing with a Trade or Dividend row.
     *
     * @param row analyze this
     * @return appropriate transformer
     */
    fun adapter(row: List<String>): TrnAdapter {
        return if (TRADE_TYPES.contains(row[ShareSightTradeAdapter.type].toUpperCase())) {
            shareSightTrade
        } else shareSightDivi
    }

    companion object {
        private val TRADE_TYPES = mutableSetOf("BUY", "SELL", "SPLIT")
    }
}
