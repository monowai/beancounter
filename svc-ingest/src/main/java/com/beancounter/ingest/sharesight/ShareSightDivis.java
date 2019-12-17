package com.beancounter.ingest.sharesight;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.ingest.reader.Transformer;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * Converts from the ShareSight dividend format.
 *
 * <p>ShareSight amounts are in Portfolio currency; BC expects values in trade currency.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
@Slf4j
public class ShareSightDivis implements Transformer {

  public static final int code = 0;
  public static final int name = 1;
  public static final int date = 2;
  public static final int fxRate = 3;
  public static final int currency = 4;
  public static final int net = 5;
  public static final int tax = 6;
  public static final int gross = 7;
  public static final int comments = 8;

  private ShareSightService shareSightService;

  @Autowired
  public ShareSightDivis(ShareSightService shareSightService) {
    this.shareSightService = shareSightService;
  }

  @Override
  public Transaction from(List<String> row, Portfolio portfolio)
      throws ParseException {

    Asset asset = shareSightService.resolveAsset(row.get(code));
    asset.getMarket().setCurrency(Currency.builder().code(row.get(currency)).build());
    BigDecimal tradeRate = new BigDecimal(row.get(fxRate));

    return Transaction.builder()
        .asset(asset)
        .portfolio(portfolio)
        .tradeCurrency(Currency.builder().code(row.get(currency)).build())
        .trnType(TrnType.DIVI)
        .tax(MathUtils.multiply(new BigDecimal(row.get(tax)), tradeRate))
        .tradeAmount(MathUtils.multiply(shareSightService.parseDouble(row.get(net)), tradeRate))
        .cashAmount(MathUtils.multiply(shareSightService.parseDouble(row.get(net)), tradeRate))
        .tradeDate(shareSightService.parseDate(row.get(date)))
        .comments(row.get(comments))
        .tradeCashRate(shareSightService.isRatesIgnored() || shareSightService.isUnset(tradeRate)
            ? null : tradeRate)
        .build()
        ;

  }

  @Override
  public boolean isValid(List<String> row) {
    if (row.size() == 9) {
      if (row.get(0).contains(".")) {
        return true;
      }
      return !row.get(0).equalsIgnoreCase("code")
          && !row.get(0).equalsIgnoreCase("total");
    }
    return false;
  }
}
