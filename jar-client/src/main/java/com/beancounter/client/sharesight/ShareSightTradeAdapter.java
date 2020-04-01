package com.beancounter.client.sharesight;

import com.beancounter.client.MarketService;
import com.beancounter.client.ingest.AssetIngestService;
import com.beancounter.client.ingest.Filter;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
  private DateUtils dateUtils = new DateUtils();
  private ShareSightConfig shareSightConfig;
  private Filter filter = new Filter(null);

  private MarketService marketService;
  private AssetIngestService assetIngestService;

  public ShareSightTradeAdapter(ShareSightConfig shareSightConfig,
                                AssetIngestService assetIngestService,
                                MarketService marketService) {
    this.marketService = marketService;
    this.assetIngestService = assetIngestService;
    this.shareSightConfig = shareSightConfig;
  }

  @Autowired(required = false)
  void setFilter(Filter filter) {
    this.filter = filter;
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
        tradeRate = MathUtils.parse(row.get(fxRate), shareSightConfig.getNumberFormat());
        fees = MathUtils.parse(row.get(brokerage), shareSightConfig.getNumberFormat());
        tradeAmount = MathUtils.parse(row.get(value), shareSightConfig.getNumberFormat());
      }

      return TrnInput.builder()
          .asset(trustedTrnRequest.getAsset().getId())
          .trnType(trnType)
          .quantity(MathUtils.parse(row.get(quantity), shareSightConfig.getNumberFormat()))
          .price(MathUtils.parse(row.get(price), shareSightConfig.getNumberFormat()))
          .fees(MathUtils.divide(fees, tradeRate))
          .tradeAmount(MathUtils.multiply(tradeAmount, tradeRate))
          .tradeDate(dateUtils.getDate(row.get(date), shareSightConfig.getDateFormat()))
          .cashCurrency(trustedTrnRequest.getPortfolio().getCurrency().getCode())
          .tradeCurrency(row.get(currency))
          // Zero and null are treated as "unknown"
          .tradeCashRate(shareSightConfig.isRatesIgnored() || MathUtils.isUnset(tradeRate)
              ? null : tradeRate)
          .comments(comment)
          .build();
    } catch (ParseException e) {
      String message = e.getMessage();
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
    String ttype = row.get(type);
    return ttype != null && !ttype.contains(".");
  }

  @Override
  public Asset resolveAsset(List<String> row) {
    String assetName = row.get(name);
    String assetCode = row.get(code);
    String marketCode = row.get(market);

    Market market = marketService.getMarket(marketCode.toUpperCase());
    Asset asset = assetIngestService.resolveAsset(assetCode, assetName, market);

    if (!filter.inFilter(asset)) {
      return null;
    }
    return asset;
  }

}
