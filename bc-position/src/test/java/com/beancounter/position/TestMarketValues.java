package com.beancounter.position;

import com.beancounter.common.model.*;
import com.beancounter.position.counter.Accumulator;
import com.beancounter.position.model.MarketValue;
import com.beancounter.position.model.Positions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author mikeh
 * @since 2019-02-07
 */
class TestMarketValues {

    @Test
    void marketValues() {
        Asset microsoft = Asset.builder()
            .id("MSFT")
            .market(Market.builder().id("NYSE").build())
            .build();

        Positions positions = new Positions(Portfolio.builder().id("TEST").build());

        Position position = positions.get(microsoft);

        assertThat(position)
            .isNotNull();

        Transaction buy = Transaction.builder()
            .trnType(TrnType.BUY)
            .asset(microsoft)
            .tradeAmount(new BigDecimal(2000))
            .quantity(new BigDecimal(100)).build();

        Accumulator accumulator = new Accumulator();
        position = accumulator.accumulate(buy, position);
        positions.add(position);

        position = positions.get(microsoft);

        assertThat(position.getQuantity())
            .hasFieldOrPropertyWithValue("purchased", new BigDecimal(100))
            .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

        MarketValue marketValue = MarketValue.builder()
            .position(position)
            .price(Price.builder().asset(microsoft).price(new BigDecimal(100d)).build())
            .build();

        assertThat(marketValue)
            .hasFieldOrPropertyWithValue("marketValue", new BigDecimal(100*100d))
            .hasFieldOrPropertyWithValue("marketCost", new BigDecimal(2000d))
        ;

        // Add second buy
        accumulator.accumulate(buy, position);
        assertThat(marketValue)
            .hasFieldOrPropertyWithValue("marketValue", new BigDecimal(200*100d))
            .hasFieldOrPropertyWithValue("marketCost", new BigDecimal(4000d))
        ;

    }
}
