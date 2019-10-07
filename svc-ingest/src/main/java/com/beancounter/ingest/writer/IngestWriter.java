package com.beancounter.ingest.writer;

import java.io.OutputStream;

public interface IngestWriter {
  OutputStream prepareFile(String outFile);
}
