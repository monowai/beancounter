package com.beancounter.googled.sharesight;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Converts from the ShareSight trade format.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
@Log4j2
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
  public static final int fxrate = 9;
  public static final int value = 10;
  public static final int comments = 11;
  private final ShareSightHelper helper;

  @Autowired
  public ShareSightTrades(ShareSightHelper helper) {
    this.helper = helper;
  }

  @Override
  public Transaction of(List row) throws ParseException {
    try {
      TrnType trnType = helper.resovleType(row.get(type).toString());
      if (trnType == null) {
        throw new BusinessException(String.format("Unsupported type %s", row.get(type).toString()));
      }

      Asset asset = Asset.builder().code(
          row.get(code).toString())
          .name(row.get(name).toString())
          .market(Market.builder().code(row.get(market).toString()).build())
          .build();

      String comment = (row.size() == 12 ? row.get(comments).toString() : null);


      BigDecimal tradeRate = BigDecimal.ZERO;
      BigDecimal tradeAmount = BigDecimal.ZERO;
      if (trnType != TrnType.SPLIT) {
        tradeRate = new BigDecimal(row.get(fxrate).toString());
        tradeAmount = helper.parseDouble(row.get(value));
      }

      return Transaction.builder()
          .asset(asset)
          .trnType(trnType)
          .quantity(helper.parseDouble(row.get(quantity).toString()))
          .price(helper.parseDouble(row.get(price).toString()))
          .fees(new BigDecimal(row.get(brokerage).toString()))
          .tradeAmount(tradeAmount.multiply(tradeRate).abs())
          .tradeDate(helper.parseDate(row.get(date).toString()))
          .tradeCurrency(row.get(currency).toString())
          .tradeRate(tradeRate) // Trade to Portfolio Reference rate
          .comments(comment)
          .build()
          ;
    } catch (RuntimeException re) {
      log.error(row);
      throw re;
    }

  }

  @Override
  public boolean isValid(List row) {
    if (row.size() > 6) {
      return !row.get(0).toString().equalsIgnoreCase("market");
    } 
    return false;
  }


}
