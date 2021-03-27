package com.beancounter.shell.ingest

import com.beancounter.common.model.Portfolio

class IngestionRequest(
    val reader: String = "CSV",
    val file: String,
    val writer: String? = null,
    val filter: String? = null,
    val provider: String? = null,
    val ratesIgnored: Boolean = true,
    val trnPersist: Boolean = true,
    val portfolioCode: String? = null,

    // Portfolio resolved from portfolioCode
    val portfolio: Portfolio? = null
)
