package com.beancounter.position.accumulation;

import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Trn;

public interface AccumulationStrategy {

  void accumulate(Trn trn, Portfolio portfolio, Position position);
}
