package com.beancounter.position.accumulation;

import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;

public interface ValueTransaction {

  void value(Transaction transaction, Position position);
}
