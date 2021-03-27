package com.beancounter.client.sharesight;

import com.beancounter.client.ingest.AssetIngestService;
import com.beancounter.client.ingest.Filter;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnImportRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CallerRef;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.common.utils.NumberUtils;
import com.beancounter.common.utils.TradeCalculator;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
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
public class ShareSightTradeAdapter implements TrnAdapter {
  public static final int id = 0;
  public static final int market = 1;
  public static final int code = 2;
  public static final int name = 3;
  public static final int type = 4;
  public static final int date = 5;
  public static final int quantity = 6;
  public static final int price = 7;
  public static final int brokerage = 8;
  public static final int currency = 9;
  public static final int fxRate = 10;
  public static final int value = 11;
  public static final int comments = 12;
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ShareSightTradeAdapter.class);
  private final DateUtils dateUtils;
  private final NumberUtils numberUtils = new NumberUtils();

  private final ShareSightConfig shareSightConfig;
  private final AssetIngestService assetIngestService;
  private final TradeCalculator tradeCalculator;
  private Filter filter = new Filter(null);

  public ShareSightTradeAdapter(ShareSightConfig shareSightConfig,
                                AssetIngestService assetIngestService,
                                DateUtils dateUtils,
                                TradeCalculator tradeCalculator) {
    this.assetIngestService = assetIngestService;
    this.shareSightConfig = shareSightConfig;
    this.dateUtils = dateUtils;
    this.tradeCalculator = tradeCalculator;
  }

  @Autowired(required = false)
  void setFilter(Filter filter) {
    this.filter = filter;
  }

  @NonNull
  @Override
  public TrnInput from(TrustedTrnImportRequest trustedTrnImportRequest) {
    assert trustedTrnImportRequest != null;
    List<String> row = trustedTrnImportRequest.getRow();
    String ttype = row.get(type);
    if (ttype == null || ttype.equalsIgnoreCase("")) {
      throw new BusinessException(String.format("Unsupported type %s", row.get(type)));
    }
    TrnType trnType = TrnType.valueOf(ttype.toUpperCase());

    String comment = (row.size() == 13 ? nullSafe(row.get(comments)) : null);

    BigDecimal tradeRate = null;
    BigDecimal fees = BigDecimal.ZERO;
    BigDecimal tradeAmount = BigDecimal.ZERO;

    try {
      if (trnType != TrnType.SPLIT) {
        tradeRate = MathUtils.parse(row.get(fxRate), shareSightConfig.getNumberFormat());
        fees = calcFees(row, tradeRate);
        tradeAmount = calcTradeAmount(row, tradeRate);
      }
      Asset asset = resolveAsset(row);
      if (asset == null) {
        log.error("Unable to resolve asset [{}]", row);
        throw new BusinessException("Unable to resolve asset [%s]\", row");
      }
      TrnInput trnInput = new TrnInput(
          new CallerRef(trustedTrnImportRequest.getPortfolio().getId(), null, row.get(id)),
          asset.getId(),
          trnType,
          Objects.requireNonNull(MathUtils.parse(row.get(quantity),
              shareSightConfig.getNumberFormat())),
          row.get(currency),
          dateUtils.getDate(row.get(date),
              shareSightConfig.getDateFormat(),
              dateUtils.getZoneId()),
          fees,
          MathUtils.nullSafe(MathUtils.parse(row.get(price), shareSightConfig.getNumberFormat())),
          tradeAmount,
          comment
      );

      trnInput.setCashCurrency(trustedTrnImportRequest.getPortfolio().getCurrency().getCode());
      // Zero and null are treated as "unknown"
      trnInput.setTradeCashRate(getTradeCashRate(tradeRate));
      return trnInput;
    } catch (ParseException e) {
      String message = e.getMessage();
      log.error("{} - {} Parsing row {}",
          message,
          "TRADE",
          row);
      throw new BusinessException(message);
    }

  }

  private BigDecimal calcFees(List<String> row, BigDecimal tradeRate) throws ParseException {
    BigDecimal result = MathUtils.parse(row.get(brokerage), shareSightConfig.getNumberFormat());
    if (shareSightConfig.isCalculateAmount() || result == null) {
      return (result == null ? BigDecimal.ZERO : result);
    } else {
      return MathUtils.divide(result, tradeRate);
    }
  }

  private BigDecimal getTradeCashRate(BigDecimal tradeRate) {
    return shareSightConfig.isCalculateRates() || numberUtils.isUnset(tradeRate)
        ? null : tradeRate;
  }

  private BigDecimal calcTradeAmount(List<String> row, BigDecimal tradeRate) throws ParseException {
    BigDecimal result = MathUtils.parse(row.get(value), shareSightConfig.getNumberFormat());
    if (shareSightConfig.isCalculateAmount() || result == null) {
      BigDecimal q = new BigDecimal(row.get(quantity));
      BigDecimal p = new BigDecimal(row.get(price));
      BigDecimal f = MathUtils.nullSafe(MathUtils.get(row.get(brokerage)));
      result = tradeCalculator.amount(q, p, f);
    } else {
      // ShareSight store tradeAmount it portfolio currency, BC stores in Trade CCY
      result = MathUtils.multiply(result, tradeRate);
    }
    return result;
  }

  private String nullSafe(Object o) {
    return o == null ? null : o.toString();
  }

  @Override
  public boolean isValid(List<String> row) {
    String ttype = row.get(type);
    return ttype != null && !ttype.contains(".");
  }

  @Override
  public Asset resolveAsset(List<String> row) {
    String assetCode = row.get(code);
    String marketCode = row.get(market);

    Asset asset = assetIngestService.resolveAsset(marketCode, assetCode);

    if (!filter.inFilter(asset)) {
      return null;
    }
    return asset;
  }

}
