package com.beancounter.shell.ingest

import com.beancounter.common.exception.SystemException
import com.beancounter.shell.csv.CsvIngester
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * All known Ingestion request handlers.
 */
@Service
class IngestionFactory {
    private val ingesterMap: MutableMap<String, Ingester> = HashMap()

    @Autowired
    fun setCsvIngester(csvIngester: CsvIngester) {
        add("CSV", csvIngester)
    }

    fun getIngester(ingestionRequest: IngestionRequest): Ingester {
        return ingesterMap[ingestionRequest.reader.uppercase(Locale.getDefault())]
            ?: throw SystemException("Unable to resolve ingestor for ${ingestionRequest.reader}")
    }

    fun add(key: String, ingester: Ingester) {
        ingesterMap[key.uppercase(Locale.getDefault())] = ingester
    }
}
