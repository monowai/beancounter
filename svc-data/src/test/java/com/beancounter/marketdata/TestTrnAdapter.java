package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.CallerRef;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.CurrencyUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.assets.AssetService;
import com.beancounter.marketdata.currency.CurrencyService;
import com.beancounter.marketdata.portfolio.PortfolioService;
import com.beancounter.marketdata.trn.TrnAdapter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;


@SpringBootTest(classes = {
    TrnAdapter.class})
class TestTrnAdapter {
  @MockBean
  private PortfolioService portfolioService;
  @MockBean
  private AssetService assetService;
  @MockBean
  private CurrencyService currencyService;
  @Autowired
  private TrnAdapter trnAdapter;

  @Test
  void is_InputToTrn() {
    TrnInput trnInput = TrnInput.builder()
        .callerRef(CallerRef.builder().batch("1").callerId("1").provider("ABC").build())
        .trnType(TrnType.BUY)
        .asset(AssetUtils.toKey("MSFT", "NASDAQ"))
        .cashAsset(AssetUtils.toKey("USD-X", "USER"))
        .tradeDate(new DateUtils().getDate("2019-10-10"))
        .settleDate(new DateUtils().getDate("2019-10-10"))
        .fees(BigDecimal.ONE)
        .cashAmount(new BigDecimal("100.99"))
        .tradeAmount(new BigDecimal("100.99"))
        .price(new BigDecimal("10.99"))
        .tradeBaseRate(new BigDecimal("1.99"))
        .tradeCashRate(new BigDecimal("1.99"))
        .tradePortfolioRate(new BigDecimal("10.99"))
        .tradeBaseRate(BigDecimal.ONE)
        .tradeCurrency("USD")
        .cashCurrency("USD")
        .comments("Comment")
        .build();
    Collection<TrnInput> trnInputCollection = new ArrayList<>();
    trnInputCollection.add(trnInput);
    TrnRequest trnRequest = TrnRequest.builder()
        .portfolioId("abc")
        .data(trnInputCollection).build();

    Mockito.when(portfolioService.find("abc"))
        .thenReturn(Portfolio.builder().id("ABC").build());
    Mockito.when(assetService.find(trnInput.getAsset()))
        .thenReturn(AssetUtils.fromKey(trnInput.getAsset()));
    Mockito.when(currencyService.getCode("USD"))
        .thenReturn(CurrencyUtils.getCurrency("USD"));

    TrnResponse trnResponse = trnAdapter.convert(portfolioService.find("abc"), trnRequest);
    assertThat(trnResponse).isNotNull();
    assertThat(trnResponse.getData()).hasSize(1);
    assertThat(trnResponse.getData().iterator().next())
        .hasFieldOrPropertyWithValue("quantity", trnInput.getQuantity())
        .hasFieldOrPropertyWithValue("tradeDate", trnInput.getTradeDate())
        .hasFieldOrPropertyWithValue("settleDate", trnInput.getSettleDate())
        .hasFieldOrPropertyWithValue("fees", trnInput.getFees())
        .hasFieldOrPropertyWithValue("cashAmount", trnInput.getCashAmount())
        .hasFieldOrPropertyWithValue("tradeAmount", trnInput.getTradeAmount())
        .hasFieldOrPropertyWithValue("price", trnInput.getPrice())
        .hasFieldOrPropertyWithValue("tradeBaseRate", trnInput.getTradeBaseRate())
        .hasFieldOrPropertyWithValue("tradeCashRate", trnInput.getTradeCashRate())
        .hasFieldOrPropertyWithValue("tradePortfolioRate", trnInput.getTradePortfolioRate())
        .hasFieldOrPropertyWithValue("tradeBaseRate", trnInput.getTradeBaseRate())
        .hasFieldOrPropertyWithValue("tradeCurrency.code", trnInput.getTradeCurrency())
        .hasFieldOrPropertyWithValue("cashCurrency.code", trnInput.getCashCurrency())
        .hasFieldOrPropertyWithValue("trnType", trnInput.getTrnType())
        .hasFieldOrPropertyWithValue("comments", trnInput.getComments())
    ;
  }
}
