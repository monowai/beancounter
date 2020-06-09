package com.beancounter.event.service;

import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Trn;

public interface Event {
  Trn generate(Portfolio portfolio, Position currentPosition, CorporateEvent corporateEvent);
}
