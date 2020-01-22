package com.beancounter.shell.sharesight;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.shell.reader.Transformer;
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
public class ShareSightTrades implements Transformer {
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
  private final ShareSightService shareSightService;

  @Autowired
  public ShareSightTrades(ShareSightService shareSightService) {
    this.shareSightService = shareSightService;
  }

  @Override
  public Trn from(List<String> row, Portfolio portfolio)
      throws ParseException {
    try {
      TrnType trnType = shareSightService.resolveType(row.get(type));
      if (trnType == null) {
        throw new BusinessException(String.format("Unsupported type %s",
            row.get(type)));
      }
      String assetName = row.get(name);
      String assetCode = row.get(code);
      String marketCode = row.get(market);

      Asset asset = shareSightService.resolveAsset(assetCode, assetName, marketCode);

      String comment = (row.size() == 12 ? row.get(comments) : null);

      BigDecimal tradeRate = null;
      BigDecimal fees = null;
      BigDecimal tradeAmount = BigDecimal.ZERO;
      if (trnType != TrnType.SPLIT) {
        Object rate = row.get(fxRate);
        tradeRate = getBigDecimal(rate);
        Object fee = row.get(brokerage);
        fees = getBigDecimal(fee);
        tradeAmount = shareSightService.parseDouble(row.get(value));
      }

      return Trn.builder()
          .asset(asset)
          .trnType(trnType)
          .quantity(shareSightService.parseDouble(row.get(quantity)))
          .price(shareSightService.parseDouble(row.get(price)))
          .fees(shareSightService.safeDivide(
              fees, tradeRate))
          .tradeAmount(shareSightService.getValueWithFx(tradeAmount, tradeRate))
          .tradeDate(shareSightService.parseDate(row.get(date)))
          .portfolioId(portfolio.getId())
          .cashCurrency(portfolio.getCurrency())
          .tradeCurrency(Currency.builder().code(row.get(currency)).build())
          // Zero and null are treated as "unknown"
          .tradeCashRate(shareSightService.isRatesIgnored() || shareSightService.isUnset(tradeRate)
              ? null : tradeRate)
          .comments(comment)
          .build()
          ;
    } catch (RuntimeException re) {
      log.error(String.valueOf(row));
      throw re;
    }

  }

  private BigDecimal getBigDecimal(Object rate) {
    if (rate != null) {
      return new BigDecimal(rate.toString());
    }
    return null;
  }

  @Override
  public boolean isValid(List<String> row) {
    if (row.size() > 6) {
      return !row.get(0).equalsIgnoreCase("market");
    }
    return false;
  }

}
