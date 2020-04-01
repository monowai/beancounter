package com.beancounter.marketdata.trn;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.CallerRef;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.marketdata.assets.AssetService;
import com.beancounter.marketdata.currency.CurrencyService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TrnAdapter {
  AssetService assetService;
  CurrencyService currencyService;

  TrnAdapter(AssetService assetService, CurrencyService currencyService) {
    this.assetService = assetService;
    this.currencyService = currencyService;
  }

  public TrnResponse convert(Portfolio portfolio, TrnRequest trnRequest) {
    TrnResponse trnResponse = TrnResponse.builder().build();
    for (TrnInput trnInput : trnRequest.getData()) {
      trnResponse.getData().add(map(portfolio, trnInput));
    }
    return trnResponse;

  }

  public Trn map(Portfolio portfolio, TrnInput trnInput) {
    return Trn.builder()
        .id(KeyGenUtils.format(UUID.randomUUID()))
        .callerRef(CallerRef.from(trnInput.getCallerRef()))
        .tradeDate(trnInput.getTradeDate())
        .settleDate(trnInput.getSettleDate())
        .cashAmount(trnInput.getCashAmount())
        .tax(trnInput.getTax())
        .fees(trnInput.getFees())
        .tradeAmount(trnInput.getTradeAmount())
        .cashAmount(trnInput.getCashAmount())
        .quantity(trnInput.getQuantity())
        .portfolio(portfolio)
        .price(trnInput.getPrice())
        .tradePortfolioRate(trnInput.getTradePortfolioRate())
        .tradeCashRate(trnInput.getTradeCashRate())
        .tradeBaseRate(trnInput.getTradeBaseRate())
        .trnType(trnInput.getTrnType())
        .comments(trnInput.getComments())
        .asset(assetService.find(trnInput.getAsset()))
        .cashAsset(trnInput.getCashAsset() == null
            ? null : assetService.find((trnInput.getCashAsset())))
        .cashCurrency(trnInput.getCashCurrency() == null
            ? null : currencyService.getCode(trnInput.getCashCurrency()))
        .tradeCurrency(currencyService.getCode(trnInput.getTradeCurrency()))
        .build();
  }

  public Asset hydrate(Asset asset) {
    if (asset == null) {
      return null;
    }
    return assetService.find(asset.getId());
  }
}
