package com.beancounter.ingest.reader;

import com.beancounter.common.model.Transaction;
import com.beancounter.ingest.model.IngestionRequest;
import java.util.Collection;

public interface Ingester {

  Collection<Transaction> ingest(IngestionRequest ingestionRequest);

}
