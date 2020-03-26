package com.beancounter.client.sharesight;

import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.TrnType;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

/**
 * Converts from the ShareSight trade format.
 *
 * <p>ShareSight amounts are in Portfolio currency; BC expects values in trade currency.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
@Slf4j
@Configuration
public class ShareSightTradeAdapter implements TrnAdapter {
  public static final int market = 0;
  public static final int code = 1;
  public static final int name = 2;
  public static final int type = 3;
  public static final int date = 4;
  public static final int quantity = 5;
  public static final int price = 6;
  public static final int brokerage = 7;
  public static final int currency = 8;
  public static final int fxRate = 9;
  public static final int value = 10;
  public static final int comments = 11;
  private ShareSightService shareSightService;

  @Autowired
  public ShareSightTradeAdapter(ShareSightService shareSightService) {
    this.shareSightService = shareSightService;
  }

  @Override
  public TrnInput from(TrustedTrnRequest trustedTrnRequest) {
    List<String> row = trustedTrnRequest.getRow();
    String ttype = row.get(type);
    if (ttype == null || ttype.equalsIgnoreCase("")) {
      throw new BusinessException(String.format("Unsupported type %s",
          row.get(type)));
    }
    TrnType trnType = TrnType.valueOf(ttype.toUpperCase());

    String comment = (row.size() == 12 ? nullSafe(row.get(comments)) : null);

    BigDecimal tradeRate = null;
    BigDecimal fees = null;
    BigDecimal tradeAmount = BigDecimal.ZERO;

    try {
      if (trnType != TrnType.SPLIT) {
        tradeRate = shareSightService.parseDouble(row.get(fxRate));
        fees = shareSightService.parseDouble(row.get(brokerage));
        tradeAmount = shareSightService.parseDouble(row.get(value));
      }

      return TrnInput.builder()
          .asset(trustedTrnRequest.getAsset().getId())
          .trnType(trnType)
          .quantity(shareSightService.parseDouble(row.get(quantity)))
          .price(shareSightService.parseDouble(row.get(price)))
          .fees(shareSightService.safeDivide(
              fees, tradeRate))
          .tradeAmount(shareSightService.getValueWithFx(tradeAmount, tradeRate))
          .tradeDate(shareSightService.parseDate(row.get(date)))
          .cashCurrency(trustedTrnRequest.getPortfolio().getCurrency().getCode())
          .tradeCurrency(row.get(currency))
          // Zero and null are treated as "unknown"
          .tradeCashRate(shareSightService.isRatesIgnored() || shareSightService.isUnset(tradeRate)
              ? null : tradeRate)
          .comments(comment)
          .build();
    } catch (ParseException e) {
      String message = e.getMessage();
      if (e.getCause() != null) {
        message = e.getCause().getMessage();
      }
      log.error("{} - {} Parsing row {}",
          message,
          "TRADE",
          row);
      throw new BusinessException(message);
    }

  }

  private String nullSafe(Object o) {
    return o == null ? null : o.toString();
  }

  @Override
  public boolean isValid(List<String> row) {
    if (row.size() > 6) {
      return !row.get(0).equalsIgnoreCase("market");
    }
    return false;
  }

  @Override
  public Asset resolveAsset(List<String> row) {
    String assetName = row.get(name);
    String assetCode = row.get(code);
    String marketCode = row.get(market);

    Asset asset = shareSightService.resolveAsset(assetCode, assetName, marketCode);
    if (!shareSightService.inFilter(asset)) {
      return null;
    }
    return asset;
  }

}
