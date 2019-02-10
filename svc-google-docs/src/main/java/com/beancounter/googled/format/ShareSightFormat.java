package com.beancounter.googled.format;

import static org.apache.http.client.utils.DateUtils.parseDate;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Converts from the ShareSight format.
 * @author mikeh
 * @since 2019-02-08
 */
@Service
@Log4j2
public class ShareSightFormat implements FormatReader {
  int offSet = -1;
  private int market = ++offSet;
  private int code = ++offSet;
  private int name = ++offSet;
  private int type = ++offSet;
  private int date = ++offSet;
  private int quantity = ++offSet;
  private int price = ++offSet;
  private int brokerage = ++offSet;
  private int currency = ++offSet;
  private int fxrate = ++offSet;
  private int value = ++offSet;
  private int comments = ++offSet;

  private DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

  @Override
  public Transaction of(List row) throws ParseException {
    TrnType trnType = resovleType(row.get(type).toString());
    if (trnType == null) {
      return null;
    }

    Asset asset = Asset.builder().id(
        row.get(code).toString())
        .name(row.get(name).toString())
        .market(Market.builder().id(row.get(market).toString()).build())
        .build();
    return Transaction.builder()
        .asset(asset)
        .trnType(trnType)
        .quantity(new BigDecimal(row.get(quantity).toString()))
        .price(new BigDecimal(row.get(price).toString()))
        .fees(new BigDecimal(row.get(brokerage).toString()))
        .tradeAmount(parseDouble(row.get(value)))
        .tradeDate(parseDate(row.get(date).toString()))
        .build()
        ;

  }

  private BigDecimal parseDouble(Object o) throws ParseException {
    return new BigDecimal(NumberFormat.getInstance(Locale.US).parse(o.toString()).toString());
  }

  private TrnType resovleType(String type) {
    if (type.equalsIgnoreCase("buy")) {
      return TrnType.BUY;
    } else if (type.equalsIgnoreCase("sell")) {
      return TrnType.SELL;
    }
    return null;
  }

}
