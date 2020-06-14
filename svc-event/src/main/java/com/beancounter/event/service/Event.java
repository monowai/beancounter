package com.beancounter.event.service;

import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;

public interface Event {
  TrustedTrnEvent calculate(Portfolio portfolio,
                            Position currentPosition,
                            CorporateEvent corporateEvent);
}
