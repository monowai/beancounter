package com.beancounter.client.ingest;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnImportRequest;

public interface RowAdapter {
  TrnInput transform(TrustedTrnImportRequest trustedTrnImportRequest);

}
