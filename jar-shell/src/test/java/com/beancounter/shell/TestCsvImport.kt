package com.beancounter.shell

import com.beancounter.shell.csv.CsvIngester
import com.beancounter.shell.ingest.HttpWriter
import com.beancounter.shell.ingest.IngestionRequest
import lombok.SneakyThrows
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class TestCsvImport {
    @Test
    @SneakyThrows
    fun importCsv() {
        val csvIngester = CsvIngester()
        csvIngester.prepare(
            IngestionRequest(file = "/trades.csv"),
            HttpWriter()
        )
        val results = csvIngester.values
        Assertions.assertThat(results).isNotEmpty.hasSize(5)
    }
}
