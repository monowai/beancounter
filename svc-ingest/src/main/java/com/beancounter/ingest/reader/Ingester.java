package com.beancounter.ingest.reader;

import com.beancounter.common.model.Trn;
import com.beancounter.ingest.model.IngestionRequest;
import java.util.Collection;

public interface Ingester {

  Collection<Trn> ingest(IngestionRequest ingestionRequest);

}
