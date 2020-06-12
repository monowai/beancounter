package com.beancounter.shell.ingest;

import com.beancounter.common.input.TrustedTrnImportRequest;

public interface TrnWriter {
  void reset();

  void write(TrustedTrnImportRequest trnRequest);

  /**
   * if you're writer supports batching, this tells you when we're done processing.
   */
  void flush();

  String id();
}
