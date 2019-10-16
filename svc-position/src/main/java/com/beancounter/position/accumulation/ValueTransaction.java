package com.beancounter.position.accumulation;

import com.beancounter.common.model.Transaction;
import com.beancounter.position.model.Position;

public interface ValueTransaction {

  void value(Transaction transaction, Position position);
}
