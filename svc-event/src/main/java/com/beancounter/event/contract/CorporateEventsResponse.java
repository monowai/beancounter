package com.beancounter.event.contract;

import com.beancounter.common.contracts.Payload;
import com.beancounter.common.event.CorporateEvent;
import java.util.ArrayList;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class CorporateEventsResponse implements Payload<Collection<CorporateEvent>> {
  @Builder.Default
  private Collection<CorporateEvent> data = new ArrayList<>();
}
