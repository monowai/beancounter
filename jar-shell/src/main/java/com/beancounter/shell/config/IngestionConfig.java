package com.beancounter.shell.config;

import com.beancounter.client.sharesight.ShareSightConfig;
import com.beancounter.shell.cli.IngestionCommand;
import com.beancounter.shell.csv.CsvIngester;
import com.beancounter.shell.google.GoogleConfig;
import com.beancounter.shell.ingest.HttpWriter;
import com.beancounter.shell.ingest.IngestionFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    EnvConfig.class,
    IngestionCommand.class,
    IngestionFactory.class,
    HttpWriter.class,
    ShareSightConfig.class,
    GoogleConfig.class,
    CsvIngester.class})
public class IngestionConfig {
}
