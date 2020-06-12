package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.CallerRef;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnImportRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
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
import java.util.List;
import org.junit.jupiter.api.Test;

class TestTrn {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void is_TransactionRequestSerializing() throws Exception {
    TrnInput trnInput = TrnInput.builder()
        .callerRef(CallerRef.builder().batch("1").callerId("1").provider("ABC").build())
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

    Asset asset = Asset.builder().code(
        "Test")
        .name("name")
        .market(Market.builder().code("NYSE").build())
        .build();
    Portfolio portfolio = PortfolioUtils.getPortfolio("TWEE");

    Trn trn = Trn.builder()
        .id("PK")
        .asset(asset)
        .trnType(trnType)
        .callerRef(CallerRef.builder().batch("10").callerId("10").provider("TEST").build())
        .portfolio(portfolio)
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
    CallerRef fromNull = CallerRef.from(null, null);
    assertThat(fromNull).hasNoNullFieldsOrProperties();

    CallerRef id = CallerRef.builder()
        .provider("provider")
        .batch("batch")
        .callerId("456")
        .build();
    assertThat(CallerRef.from(id, null)).isEqualToComparingFieldByField(id);
  }

  @Test
  void is_TrustedTrnRequestValid() throws Exception {
    List<String> row = new ArrayList<>();
    row.add("ABC");
    row.add("ABC");

    TrustedTrnImportRequest ttr = TrustedTrnImportRequest.builder()
        .portfolio(PortfolioUtils.getPortfolio("TWEE"))
        .row(row)
        .build();

    String json = mapper.writeValueAsString(ttr);
    TrustedTrnImportRequest fromJson = mapper.readValue(json, TrustedTrnImportRequest.class);
    assertThat(fromJson).isEqualToComparingFieldByField(ttr);
    assertThat(new TrustedTrnImportRequest()).isNotNull(); // Coverage
  }

  @Test
  void is_TrnIdDefaults() {
    CallerRef callerRef = CallerRef.builder().build();
    assertThat(callerRef).hasAllNullFieldsOrProperties();
    // No values, so defaults should be created
    assertThat(CallerRef.from(callerRef, PortfolioUtils.getPortfolio("BLAH")))
        .hasNoNullFieldsOrProperties()
        .hasFieldOrPropertyWithValue("batch", "BLAH")
    ;

    callerRef = CallerRef.builder().callerId("ABC").batch("ABC").provider("ABC").build();
    assertThat(CallerRef.from(callerRef, PortfolioUtils.getPortfolio("BLAH")))
        .hasFieldOrPropertyWithValue("batch", "ABC")
        .hasFieldOrPropertyWithValue("provider", "ABC")
        .hasFieldOrPropertyWithValue("callerId", "ABC");

    // Called ID not specified
    callerRef = CallerRef.builder().batch("ABC").provider("ABC").build();
    assertThat(CallerRef.from(callerRef, PortfolioUtils.getPortfolio("BLAH")))
        .hasFieldOrPropertyWithValue("batch", "ABC")
        .hasFieldOrPropertyWithValue("provider", "ABC")
        .hasFieldOrProperty("callerId");

  }
}
