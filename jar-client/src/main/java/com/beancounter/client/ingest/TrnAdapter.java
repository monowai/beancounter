package com.beancounter.client.ingest;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnImportRequest;
import com.beancounter.common.model.Asset;
import java.util.List;

/**
 * Convert the incoming row to a Transaction object.
 *
 * @author mikeh
 * @since 2019-02-10
 */
public interface TrnAdapter {

  TrnInput from(TrustedTrnImportRequest trustedTrnImportRequest);

  boolean isValid(List<String> row);

  Asset resolveAsset(List<String> row);
}
