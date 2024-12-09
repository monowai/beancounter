package com.beancounter.shell.ingest

import com.beancounter.common.model.Portfolio

/**
 * Necessary properties to support ingestion activities.
 */
data class IngestionRequest(
    val reader: String = "CSV",
    val file: String,
    val writer: String = "HTTP",
    val provider: String? = null,
    val ratesIgnored: Boolean = true,
    val portfolioCode: String? = null,
    val portfolio: Portfolio? = null
)