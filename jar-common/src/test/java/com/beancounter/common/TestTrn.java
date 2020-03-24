package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.TrnId;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.PortfolioUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class TestTrn {

  @Test
  void is_TransactionRequestSerializing() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    TrnInput trnInput = TrnInput.builder()
        .id(TrnId.builder().batch("1").id("1").provider("ABC").build())
        .asset(AssetUtils.toKey("MSFT", "NASDAQ"))
        .cashAsset(AssetUtils.toKey("USD-X", "USER"))
        .tradeDate(new DateUtils().getDate("2019-10-10"))
        .settleDate(new DateUtils().getDate("2019-10-10"))
        .fees(BigDecimal.ONE)
        .cashAmount(new BigDecimal("100.99"))
        .tradeAmount(new BigDecimal("100.99"))
        .price(new BigDecimal("10.99"))
        .tradeBaseRate(new BigDecimal("1.99"))
        .tradePortfolioRate(new BigDecimal("10.99"))
        .tradeBaseRate(BigDecimal.ONE)
        .tradeCurrency("USD")
        .cashCurrency("USD")
        .comments("Comment")
        .build();
    Collection<TrnInput> trnInputs = new ArrayList<>();
    trnInputs.add(trnInput);
    TrnRequest trnRequest = TrnRequest.builder()
        .data(trnInputs)
        .portfolioId("abc")
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

    Trn trn = Trn.builder()
        .asset(asset)
        .trnType(trnType)
        .id(TrnId.builder().batch("10").id("10").provider("TEST").build())
        .portfolio(PortfolioUtils.getPortfolio("TWEE"))
        .tradeDate(LocalDate.now())
        .settleDate(LocalDate.now())
        .quantity(new BigDecimal("100.01"))
        .price(new BigDecimal("22.11"))
        .fees(new BigDecimal("10"))
        .tradeAmount(new BigDecimal("999.99"))
        .build();
    Collection<Trn> trns = new ArrayList<>();
    trns.add(trn);
    TrnResponse trnResponse = TrnResponse.builder()
        .data(trns)
        .build();

    String json = mapper.writeValueAsString(trnResponse);
    TrnResponse fromJson = mapper.readValue(json, TrnResponse.class);

    assertThat(fromJson)
        .isEqualToComparingFieldByField(trnResponse);
  }

  @Test
  void is_TrnIdDefaulting() {
    TrnId fromNull = TrnId.from(null);
    assertThat(fromNull).hasNoNullFieldsOrProperties();

    TrnId id = TrnId.builder().provider("provider").batch("batch").id("456").build();
    assertThat(TrnId.from(id)).isEqualToComparingFieldByField(id);
  }
}
