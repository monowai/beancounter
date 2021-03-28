package com.beancounter.shell.ingest

interface Ingester {
    fun ingest(ingestionRequest: IngestionRequest)
}
