package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.TransactionId;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.PortfolioUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class TestTransaction {

  @Test
  void is_TransactionRequestSerializing() throws Exception {
    TrnType trnType = TrnType.BUY;
    ObjectMapper mapper = new ObjectMapper();

    Asset asset = Asset.builder().code(
        "Test")
        .name("name")
        .market(Market.builder().code("NYSE").build())
        .build();

    Transaction transaction = Transaction.builder()
        .asset(asset)
        .trnType(trnType)
        .id(TransactionId.builder().batch(10).id(10).provider("TEST").build())
        .portfolioId("TWEE")
        .tradeDate(LocalDate.now())
        .settleDate(LocalDate.now())
        .quantity(new BigDecimal("100.01"))
        .price(new BigDecimal("22.11"))
        .fees(new BigDecimal("10"))
        .tradeAmount(new BigDecimal("999.99"))
        .build();

    TrnRequest trnRequest = TrnRequest.builder()
        .transaction(transaction)
        .porfolioId(transaction.getPortfolioId())
        .build();
    String json = mapper.writeValueAsString(trnRequest);

    TrnRequest fromJson = mapper.readValue(json, TrnRequest.class);

    assertThat(fromJson)
        .isEqualToComparingFieldByField(trnRequest);
  }

  @Test
  void is_TransactionResponseSerializing() throws Exception {
    TrnType trnType = TrnType.BUY;
    ObjectMapper mapper = new ObjectMapper();

    Asset asset = Asset.builder().code(
        "Test")
        .name("name")
        .market(Market.builder().code("NYSE").build())
        .build();

    Transaction transaction = Transaction.builder()
        .asset(asset)
        .trnType(trnType)
        .id(TransactionId.builder().batch(10).id(10).provider("TEST").build())
        .portfolioId("TWEE")
        .tradeDate(LocalDate.now())
        .settleDate(LocalDate.now())
        .quantity(new BigDecimal("100.01"))
        .price(new BigDecimal("22.11"))
        .fees(new BigDecimal("10"))
        .tradeAmount(new BigDecimal("999.99"))
        .build();
    Collection<Transaction>transactions = new ArrayList<>();
    transactions.add(transaction);
    TrnResponse trnResponse = TrnResponse.builder()
        .transactions(transactions)
        .build();

    trnResponse.addPortfolio(PortfolioUtils.getPortfolio("TWEE"));
    String json = mapper.writeValueAsString(trnResponse);
    TrnResponse fromJson = mapper.readValue(json, TrnResponse.class);

    assertThat(fromJson)
        .isEqualToComparingFieldByField(trnResponse);
  }
}
