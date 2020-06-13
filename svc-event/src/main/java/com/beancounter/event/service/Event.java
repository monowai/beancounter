package com.beancounter.event.service;

import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;

public interface Event {
  TrustedTrnEvent generate(Portfolio portfolio, Position currentPosition, CorporateEvent corporateEvent);
}
