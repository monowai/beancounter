package com.beancounter.googled.sharesight;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * Converts from the ShareSight dividend format.
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

  private ShareSightHelper helper;

  @Autowired
  public ShareSightDivis(ShareSightHelper helper) {
    this.helper = helper;
  }

  @Override
  public Transaction of(List row) throws ParseException {


    Asset asset = helper.resolveAsset(row.get(code).toString());
    BigDecimal tradeRate = new BigDecimal(row.get(fxRate).toString());

    return Transaction.builder()
        .asset(asset)
        .trnType(TrnType.DIVI)
        .tax(new BigDecimal(row.get(tax).toString()))
        .tradeAmount(helper.parseDouble(row.get(net)).multiply(tradeRate)
            .setScale(2, RoundingMode.HALF_UP))
        .tradeDate(helper.parseDate(row.get(date).toString()))
        .comments(row.get(comments).toString())
        .tradeCurrency(row.get(currency).toString())
        .tradeRate(tradeRate)
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
