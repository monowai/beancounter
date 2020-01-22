package com.beancounter.shell.cli;

import com.beancounter.shell.model.IngestionRequest;
import com.beancounter.shell.reader.Ingester;
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
  private Ingester ingester;

  @Autowired
  void setIngester(Ingester ingester) {
    this.ingester = ingester;
  }

  @ShellMethod("Ingest a google sheet")
  public String ingest(
      @ShellOption(help = "ID of the Google sheet to ingest") String sheet,
      @ShellOption(help = "Portfolio code to write to") String portfolio,
      @ShellOption(help = "Comma separated asset codes to include", defaultValue = "__NULL__")
          String filter

  ) {
    IngestionRequest ingestionRequest = IngestionRequest.builder()
        .filter(filter)
        .sheetId(sheet)
        .portfolioCode(portfolio)
        .build();

    ingester.ingest(ingestionRequest);
    return "Done";
  }
}
