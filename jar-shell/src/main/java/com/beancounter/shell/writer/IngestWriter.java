package com.beancounter.shell.writer;

import java.io.OutputStream;

public interface IngestWriter {
  OutputStream prepareFile(String outFile);
}
