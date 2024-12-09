package com.beancounter.shell.ingest

/**
 * Interface to indicate the class is capable of ingesting data from various sources.
 */
interface Ingester {
    fun ingest(ingestionRequest: IngestionRequest)
}