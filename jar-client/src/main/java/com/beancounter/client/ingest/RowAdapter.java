package com.beancounter.client.ingest;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
import java.util.List;

public interface RowAdapter {
  TrnInput transform(Portfolio portfolio,
                     Asset asset,
                     List<String> values,
                     String provider);

}
