package com.beancounter.shell

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.client.ingest.RowAdapter
import com.beancounter.client.services.TrnService
import com.beancounter.shell.csv.CsvIngester
import com.beancounter.shell.ingest.HttpWriter
import com.beancounter.shell.ingest.IngestionRequest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TestCsvImport {

    private var trnService: TrnService = Mockito.mock(TrnService::class.java)

    private var rowAdapter: RowAdapter = Mockito.mock(RowAdapter::class.java)

    private var fxTransactions: FxTransactions = Mockito.mock(FxTransactions::class.java)

    @Test
    fun importCsv() {
        val csvIngester = CsvIngester()
        csvIngester.prepare(
            IngestionRequest(file = "/trades.csv"),
            HttpWriter(trnService, rowAdapter, fxTransactions)
        )
        val results = csvIngester.values
        Assertions.assertThat(results).isNotEmpty.hasSize(5)
    }
}
