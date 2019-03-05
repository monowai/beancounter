package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.model.MarketData;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Wrapper to inject the correct provider deserialization.
 * 
 * @author mikeh
 * @since 2019-03-01
 */
@JsonDeserialize(using = AlphaDeserializer.class)
public class AlphaResponse extends MarketData {


}
