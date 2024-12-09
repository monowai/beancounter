package com.beancounter.shell.config

import com.beancounter.client.config.ClientConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.shell.commands.IngestionCommand
import com.beancounter.shell.csv.CsvIngester
import com.beancounter.shell.ingest.HttpWriter
import com.beancounter.shell.ingest.IngestionFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Config all necessary classes to support ingestion activities.
 */
@Configuration
@Import(
    EnvConfig::class,
    IngestionCommand::class,
    IngestionFactory::class,
    HttpWriter::class,
    ClientConfig::class,
    ShareSightConfig::class,
    CsvIngester::class
)
class IngestionConfig