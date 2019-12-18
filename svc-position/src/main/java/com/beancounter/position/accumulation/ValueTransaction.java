package com.beancounter.position.accumulation;

import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;

public interface ValueTransaction {

  void value(Transaction transaction, Portfolio portfolio, Position position);
}
