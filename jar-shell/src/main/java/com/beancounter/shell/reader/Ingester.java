package com.beancounter.shell.reader;

import com.beancounter.common.model.Trn;
import com.beancounter.shell.model.IngestionRequest;
import java.util.Collection;

public interface Ingester {

  Collection<Trn> ingest(IngestionRequest ingestionRequest);

}
