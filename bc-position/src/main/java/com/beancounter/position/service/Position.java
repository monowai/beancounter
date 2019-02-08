package com.beancounter.position.service;

import com.beancounter.common.model.MarketData;

/**
 * @author mikeh
 * @since 2019-01-27
 */


public interface Position {

    MarketData getPrice(String assetId);
}
