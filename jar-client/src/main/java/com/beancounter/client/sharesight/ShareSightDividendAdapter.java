package com.beancounter.client.sharesight;

import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.MathUtils;
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
public class ShareSightDividendAdapter implements TrnAdapter {

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
  public ShareSightDividendAdapter(ShareSightService shareSightService) {
    this.shareSightService = shareSightService;
  }

  @Override
  public TrnInput from(TrustedTrnRequest trustedTrnRequest) {
    List<String> row = trustedTrnRequest.getRow();
    try {
      BigDecimal tradeRate = shareSightService.parseDouble(row.get(fxRate));
      return TrnInput.builder()
          .asset(trustedTrnRequest.getAsset().getId())
          .tradeCurrency(row.get(currency))
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
    } catch (NumberFormatException | ParseException e) {
      String message = e.getMessage();
      if (e.getCause() != null) {
        message = e.getCause().getMessage();
      }
      log.error("{} - {} Parsing row {}",
          message,
          "DIVI",
          row);
      throw new BusinessException(message);


    }

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

  @Override
  public Asset resolveAsset(List<String> row) {
    Asset asset = shareSightService.resolveAsset(row.get(code));
    if (!shareSightService.inFilter(asset)) {
      return null;
    }
    return asset;
  }
}
