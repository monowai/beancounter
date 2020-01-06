package com.beancounter.marketdata.providers.wtd;

import com.beancounter.common.model.MarketData;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

/**
 * Encapsulates the responses from the MarketDataProvider.
 *
 * @author mikeh
 * @since 2019-03-12
 */
@Data
public class WtdResponse {

  private Date date;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonProperty("Message")
  private String message;

  private Map<String, MarketData> data;

}
