package com.beancounter.ingest.sharesight;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.ingest.reader.Transformer;
import com.beancounter.ingest.sharesight.common.ShareSightHelper;
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

  private ShareSightHelper shareSightHelper;

  @Autowired
  public ShareSightDivis(ShareSightHelper shareSightHelper) {
    this.shareSightHelper = shareSightHelper;
  }

  @Override
  public Transaction from(List row, Portfolio portfolio, Currency baseCurrency)
      throws ParseException {

    Asset asset = shareSightHelper.resolveAsset(row.get(code).toString());
    BigDecimal tradeRate = new BigDecimal(row.get(fxRate).toString());

    return Transaction.builder()
        .asset(asset)
        .portfolio(portfolio)
        .baseCurrency(baseCurrency)
        .tradeCurrency(Currency.builder().code(row.get(currency).toString()).build())
        .trnType(TrnType.DIVI)
        .tax(shareSightHelper.getMathHelper()
            .multiply(new BigDecimal(row.get(tax).toString()), tradeRate))
        .tradeAmount(shareSightHelper.getMathHelper()
            .multiply(shareSightHelper.parseDouble(row.get(net)), tradeRate))
        .cashAmount(shareSightHelper.getMathHelper()
            .multiply(shareSightHelper.parseDouble(row.get(net)), tradeRate))
        .tradeDate(shareSightHelper.parseDate(row.get(date).toString()))
        .comments(row.get(comments).toString())
        .tradeCashRate(shareSightHelper.isRatesIgnored() || shareSightHelper.isUnset(tradeRate)
            ? null : tradeRate)
        .build()
        ;

  }

  @Override
  public boolean isValid(List row) {
    if (row.size() == 9) {
      if (row.get(0).toString().contains(".")) {
        return true;
      }
      return !row.get(0).toString().equalsIgnoreCase("code")
          && !row.get(0).toString().equalsIgnoreCase("total");
    }
    return false;
  }
}
