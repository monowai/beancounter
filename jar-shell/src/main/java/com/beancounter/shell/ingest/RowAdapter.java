package com.beancounter.shell.ingest;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Portfolio;
import java.util.Collection;
import java.util.List;

public interface RowAdapter {
  Collection<TrnInput> transform(Portfolio portfolio,
                                 List<List<Object>> values,
                                 String provider);

}
