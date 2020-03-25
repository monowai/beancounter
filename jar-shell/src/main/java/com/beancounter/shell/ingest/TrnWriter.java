package com.beancounter.shell.ingest;

import com.beancounter.common.input.TrnInput;
import java.util.Collection;
import java.util.List;

public interface TrnWriter {
  void write(IngestionRequest ingestionRequest, List<String> row);

  Collection<TrnInput> flush(IngestionRequest ingestionRequest);
}
