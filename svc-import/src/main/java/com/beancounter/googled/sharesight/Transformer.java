package com.beancounter.googled.sharesight;

import com.beancounter.common.model.Transaction;
import java.text.ParseException;
import java.util.List;

/**
 * Convert the incoming row to a Transaction object.
 * @author mikeh
 * @since 2019-02-10
 */
public interface Transformer {

  Transaction of(List row) throws ParseException;

  boolean isValid(List row);

}
