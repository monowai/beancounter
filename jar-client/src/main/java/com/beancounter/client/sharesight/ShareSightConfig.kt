package com.beancounter.client.sharesight

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.Filter
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.utils.DateUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.text.NumberFormat
import java.util.Locale

/**
 * Defaults for importing CSV data in the Sharesight format.
 */
@Configuration
@Import(DateUtils::class, ShareSightDividendAdapter::class, ShareSightTradeAdapter::class, ShareSightFactory::class, FxTransactions::class, AssetIngestService::class, Filter::class, ShareSightRowAdapter::class)
class ShareSightConfig {
    val numberFormat: NumberFormat = NumberFormat.getInstance(Locale.US)

    @Value("\${date.format:dd/MM/yyyy}")
    lateinit var dateFormat: String

    // Backfill FX rates ignoring source file values
    @Value("\${rates:true}")
    val isCalculateRates = true

    // Calculate the tradeAmount field and ignore source file value
    @Value("\${amount:true}")
    val isCalculateAmount = true
}
