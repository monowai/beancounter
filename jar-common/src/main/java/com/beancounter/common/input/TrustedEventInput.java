package com.beancounter.common.input;


import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.model.Portfolio;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustedEventInput {
  private Portfolio portfolio;
  private CorporateEvent event;
}
