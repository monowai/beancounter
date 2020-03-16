package com.beancounter.shell.ingest;

import com.beancounter.common.input.TrnInput;
import java.util.Collection;

public interface Ingester {

  Collection<TrnInput> ingest(IngestionRequest ingestionRequest);

}
