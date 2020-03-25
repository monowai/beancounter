package com.beancounter.shell.ingest;

import java.util.List;

public interface TrnWriter {
  void write(IngestionRequest ingestionRequest, List<String> row);

  void flush(IngestionRequest ingestionRequest);

  String id();
}
