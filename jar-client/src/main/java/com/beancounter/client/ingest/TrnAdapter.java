package com.beancounter.client.ingest;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
import java.util.List;

/**
 * Convert the incoming row to a Transaction object.
 *
 * @author mikeh
 * @since 2019-02-10
 */
public interface TrnAdapter {

  TrnInput from(List<String> row, Portfolio portfolio, Asset asset);

  boolean isValid(List<String> row);

  Asset resolveAsset(List<String> row);
}
