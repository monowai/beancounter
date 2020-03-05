package com.beancounter.common.contracts;

import com.beancounter.common.model.MarketData;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceResponse implements Payload<Collection<MarketData>> {

  private Collection<MarketData> data;

}
