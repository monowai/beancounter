package com.beancounter.shell.ingest;

import com.beancounter.common.input.TrustedTrnRequest;

public interface TrnWriter {
  void reset();

  void write(TrustedTrnRequest trnRequest);

  /**
   * if you're writer supports batching, this tells you when we're done processing.
   */
  void flush();

  String id();
}
