package com.beancounter.event.contract;

import com.beancounter.common.contracts.Payload;
import com.beancounter.common.event.CorporateEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class CorporateEventResponse implements Payload<CorporateEvent> {
  private CorporateEvent data;
}
