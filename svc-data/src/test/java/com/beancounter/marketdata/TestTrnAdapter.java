package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.CallerRef;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.CurrencyUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.PortfolioUtils;
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
  private final CurrencyUtils currencyUtils = new CurrencyUtils();
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
    TrnInput trnInput = new TrnInput(
        new CallerRef("ABC", "1", "1"),
        AssetUtils.toKey("MSFT", "NASDAQ"),
        TrnType.BUY, BigDecimal.TEN);

    trnInput.setCashAsset(AssetUtils.toKey("USD-X", "USER"));
    trnInput.setTradeDate(new DateUtils().getDate("2019-10-10"));
    trnInput.setSettleDate(new DateUtils().getDate("2019-10-10"));
    trnInput.setFees(BigDecimal.ONE);
    trnInput.setCashAmount(new BigDecimal("100.99"));
    trnInput.setTradeAmount(new BigDecimal("100.99"));
    trnInput.setPrice(new BigDecimal("10.99"));
    trnInput.setTradeBaseRate(new BigDecimal("1.99"));
    trnInput.setTradeCashRate(new BigDecimal("1.99"));
    trnInput.setTradePortfolioRate(new BigDecimal("10.99"));
    trnInput.setTradeBaseRate(BigDecimal.ONE);
    trnInput.setTradeCurrency("USD");
    trnInput.setCashCurrency("USD");
    trnInput.setComments("Comment");
    Collection<TrnInput> trnInputCollection = new ArrayList<>();
    trnInputCollection.add(trnInput);
    TrnRequest trnRequest = new TrnRequest("abc", trnInputCollection);

    Mockito.when(portfolioService.find("abc"))
        .thenReturn(PortfolioUtils.getPortfolio("abc"));
    Mockito.when(assetService.find(trnInput.getAssetId()))
        .thenReturn(AssetUtils.fromKey(trnInput.getAssetId()));
    Mockito.when(currencyService.getCode("USD"))
        .thenReturn(currencyUtils.getCurrency("USD"));

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
        .hasFieldOrPropertyWithValue("quantity", trnInput.getQuantity())
        .hasFieldOrPropertyWithValue("version", "1")
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
