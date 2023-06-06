package com.beancounter.shell.commands

import com.beancounter.shell.ingest.IngestionFactory
import com.beancounter.shell.ingest.IngestionRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

/**
 * Ingestion command.
 * ingest --sheet "1a0EOYzNj4Ru2zGS76EQimznLGJ8" --portfolio TEST
 */
@ShellComponent
class IngestionCommand {
    @Autowired
    private lateinit var ingestionFactory: IngestionFactory

    @ShellMethod("Ingest a google sheet")
    fun ingest(
        @ShellOption(help = "CSV, GSHEET", defaultValue = "CSV") reader: String = "CSV",
        @ShellOption(help = "HTTP, KAFKA", defaultValue = "HTTP") writer: String = "HTTP",
        @ShellOption(help = "ID of the item to import - file name, sheetId") file: String,
        @ShellOption(help = "Portfolio code to write to") portfolio: String,
    ): String {
        val ingestionRequest =
            IngestionRequest(reader = reader, file = file, writer = writer, portfolioCode = portfolio)
        ingestionFactory.getIngester(ingestionRequest).ingest(ingestionRequest)
        return "Done"
    }
}
