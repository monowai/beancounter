package com.beancounter.marketdata.service;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * @author mikeh
 * @since 2019-01-28
 */
@Service
public class MarketDataService implements MarketDataProvider {
    @Override
    public MarketData getCurrent(Asset asset) {
        if (asset.getId().equalsIgnoreCase("123")) {
            throw new BusinessException(
                String.format("Invalid asset code [%s]", asset.getId()));
        }

        return MarketData.builder()
            .assetId(asset.getId())
            .close(BigDecimal.valueOf(999.99))
            .open(BigDecimal.valueOf(999.99))
            .build();
    }
}
