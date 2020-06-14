package com.beancounter.common.input;


import com.beancounter.common.contracts.Payload;
import com.beancounter.common.event.CorporateEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustedEventInput implements Payload<CorporateEvent> {
  private CorporateEvent data;
}
