package com.beancounter.shell.ingest;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Portfolio;
import java.util.List;

public interface RowAdapter {
  TrnInput transform(Portfolio portfolio,
                                 List<String> values,
                                 String provider);

}
