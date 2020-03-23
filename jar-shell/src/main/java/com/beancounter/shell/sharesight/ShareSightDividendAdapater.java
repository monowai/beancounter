package com.beancounter.shell.sharesight;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.shell.ingest.TrnAdapter;
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
public class ShareSightDividendAdapater implements TrnAdapter {

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
  public ShareSightDividendAdapater(ShareSightService shareSightService) {
    this.shareSightService = shareSightService;
  }

  @Override
  public TrnInput from(List<Object> row, Portfolio portfolio)
      throws ParseException {

    Asset asset = shareSightService.resolveAsset(row.get(code).toString());
    if (!shareSightService.inFilter(asset)) {
      return null;
    }

    BigDecimal tradeRate = new BigDecimal(row.get(fxRate).toString());

    return TrnInput.builder()
        .asset(asset.getId())
        .tradeCurrency(row.get(currency).toString())
        .trnType(TrnType.DIVI)
        .tax(MathUtils.multiply(new BigDecimal(row.get(tax).toString()), tradeRate))
        .tradeAmount(MathUtils.multiply(shareSightService.parseDouble(row.get(net)), tradeRate))
        .cashAmount(MathUtils.multiply(shareSightService.parseDouble(row.get(net)), tradeRate))
        .tradeDate(shareSightService.parseDate(row.get(date)))
        .comments(row.get(comments).toString())
        .tradeCashRate(shareSightService.isRatesIgnored() || shareSightService.isUnset(tradeRate)
            ? null : tradeRate)
        .build()
        ;

  }

  @Override
  public boolean isValid(List<Object> row) {
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
