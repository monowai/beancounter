package com.beancounter.ingest.reader;

import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import java.text.ParseException;
import java.util.List;

/**
 * Convert the incoming row to a Transaction object.
 *
 * @author mikeh
 * @since 2019-02-10
 */
public interface Transformer {

  Trn from(List<String> row, Portfolio portfolio) throws ParseException;

  boolean isValid(List<String> row);

}
