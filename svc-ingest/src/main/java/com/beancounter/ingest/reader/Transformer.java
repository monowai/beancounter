package com.beancounter.ingest.reader;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import java.text.ParseException;
import java.util.List;

/**
 * Convert the incoming row to a Transaction object.
 *
 * @author mikeh
 * @since 2019-02-10
 */
public interface Transformer {

  Transaction from(List row, Portfolio portfolio, Currency baseCurrency) throws ParseException;

  boolean isValid(List row);

}
