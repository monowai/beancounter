package com.beancounter.client.ingest;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;

public interface RowAdapter {
  TrnInput transform(TrustedTrnRequest trustedTrnRequest);

}
