package com.beancounter.marketdata.service;


import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;

/**
 * @author mikeh
 * @since 2019-01-27
 */
public interface MarketDataProvider {
    MarketData getCurrent(Asset asset);
}
