package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TestTransaction {

  @Test
  void jsonSerialization() throws Exception {
    TrnType trnType = TrnType.BUY;
    ObjectMapper mapper = new ObjectMapper();

    Asset asset = Asset.builder().id(
        "Test")
        .name("name")
        .market(Market.builder().id("NYSE").build())
        .build();
    Transaction transaction = Transaction.builder()
        .asset(asset)
        .trnType(trnType)
        .quantity(new BigDecimal(100.01))
        .price(new BigDecimal(22.11))
        .fees(new BigDecimal(10))
        .tradeAmount(new BigDecimal(999.99))
        .build();

    String json = mapper.writeValueAsString(transaction);

    Transaction fromJson = mapper.readValue(json, Transaction.class);

    assertThat(fromJson)
        .isEqualToComparingFieldByField(transaction);
  }
}
