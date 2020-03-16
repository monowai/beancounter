package com.beancounter.shell.reader;

import com.beancounter.common.input.TrnInput;
import com.beancounter.shell.model.IngestionRequest;
import java.util.Collection;

public interface Ingester {

  Collection<TrnInput> ingest(IngestionRequest ingestionRequest);

}
