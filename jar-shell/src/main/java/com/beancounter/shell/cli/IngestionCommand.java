package com.beancounter.shell.cli;

import com.beancounter.shell.ingest.IngestionFactory;
import com.beancounter.shell.ingest.IngestionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

/**
 * Ingestion command.
 * ingest --sheet "1a0EOYzNj4Ru2zGS76EQimznLGJ8" --portfolio TEST
 */
@Slf4j
@ShellComponent
public class IngestionCommand {
  private IngestionFactory ingestionFactory;

  @Autowired
  void setIngestionFactory(IngestionFactory ingestionFactory) {
    this.ingestionFactory = ingestionFactory;
  }

  @ShellMethod("Ingest a google sheet")
  public String ingest(
      @ShellOption(help = "CSV, GSHEET", defaultValue = "GSHEET") String type,
      @ShellOption(help = "ID of the item to import - file name, sheetId") String file,
      @ShellOption(help = "Portfolio code to write to") String portfolio,
      @ShellOption(help = "Comma separated asset codes to include", defaultValue = "__NULL__")
          String filter

  ) {
    IngestionRequest ingestionRequest = IngestionRequest.builder()
        .filter(filter)
        .file(file)
        .type(type)
        .portfolioCode(portfolio)
        .build();

    ingestionFactory.getIngester(ingestionRequest).ingest(ingestionRequest);
    return "Done";
  }
}
