package com.beancounter.common.contracts;

import com.beancounter.common.model.CorporateEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventRequest implements Payload<CorporateEvent> {
  private CorporateEvent data;
}
