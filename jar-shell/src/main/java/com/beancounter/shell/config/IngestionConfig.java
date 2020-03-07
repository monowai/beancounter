package com.beancounter.shell.config;

import com.beancounter.shell.cli.IngestionCommand;
import com.beancounter.shell.reader.SheetReader;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    EnvConfig.class,
    SheetReader.class,
    IngestionCommand.class,
    GoogleAuthConfig.class})
public class IngestionConfig {
}
