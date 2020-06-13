package com.beancounter.common.input;

import com.beancounter.common.model.Portfolio;

public interface TrnImport {
  Portfolio getPortfolio();
  String getMessage();
}
