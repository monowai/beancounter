package com.beancounter.position;

import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.counter.Accumulator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author mikeh
 * @since 2019-02-07
 */

 class TestQuantityAccumulation {

    @Test
    void quantitiesTotalCorrectly() {
        Transaction buy = Transaction.builder()
            .trnType(TrnType.BUY)
            .tradeAmount(new BigDecimal(2000))
            .quantity(new BigDecimal(100)).build();
        
        Transaction sell = Transaction.builder()
            .trnType(TrnType.SELL)
            .quantity(new BigDecimal(100)).build();

        Accumulator accumulator = new Accumulator();

        Position position = Position.builder().build();

        assertThat(position.getQuantity())
            .hasFieldOrPropertyWithValue("total", new BigDecimal(0));

        position = accumulator.accumulate(buy, position);
        assertThat(position.getQuantity())
            .hasFieldOrPropertyWithValue("purchased", new BigDecimal(100))
            .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

        position = accumulator.accumulate(buy, position);
        assertThat(position.getQuantity())
            .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
            .hasFieldOrPropertyWithValue("sold", new BigDecimal(0))
            .hasFieldOrPropertyWithValue("total", new BigDecimal(200));

        position = accumulator.accumulate(sell, position);
        assertThat(position.getQuantity())
            .hasFieldOrPropertyWithValue("sold", new BigDecimal(-100))
            .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
            .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

        position = accumulator.accumulate(sell, position);
        assertThat(position.getQuantity())
            .hasFieldOrPropertyWithValue("sold", new BigDecimal(-200))
            .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
            .hasFieldOrPropertyWithValue("total", new BigDecimal(0));

    }
}
