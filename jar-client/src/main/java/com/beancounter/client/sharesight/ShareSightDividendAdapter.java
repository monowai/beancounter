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
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
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
  private ShareSightConfig shareSightConfig;
  private Filter filter = new Filter(null);

  private DateUtils dateUtils = new DateUtils();

  private MarketService marketService;
  private AssetIngestService assetIngestService;

  public ShareSightDividendAdapter(ShareSightConfig shareSightConfig,
                                   AssetIngestService assetIngestService,
                                   MarketService marketService) {
    this.shareSightConfig = shareSightConfig;
    this.marketService = marketService;
    this.assetIngestService = assetIngestService;
  }

  @Autowired(required = false)
  void setFilter(Filter filter) {
    this.filter = filter;
  }

  @Override
  public TrnInput from(TrustedTrnRequest trustedTrnRequest) {
    List<String> row = trustedTrnRequest.getRow();
    try {
      BigDecimal tradeRate = MathUtils.parse(row.get(fxRate), shareSightConfig.getNumberFormat());
      return TrnInput.builder()
          .asset(trustedTrnRequest.getAsset().getId())
          .tradeCurrency(row.get(currency))
          .trnType(TrnType.DIVI)
          .tax(MathUtils.multiply(new BigDecimal(row.get(tax)), tradeRate))
          .tradeAmount(
              MathUtils.multiply(
                  MathUtils.parse(row.get(net), shareSightConfig.getNumberFormat()),
                  tradeRate))
          .cashAmount(MathUtils.multiply(
              MathUtils.parse(row.get(net), shareSightConfig.getNumberFormat()),
              tradeRate))
          .tradeDate(dateUtils.getDate(row.get(date), shareSightConfig.getDateFormat()))
          .comments(row.get(comments))
          .tradeCashRate(shareSightConfig.isRatesIgnored() || MathUtils.isUnset(tradeRate)
              ? null : tradeRate)
          .build()
          ;
    } catch (NumberFormatException | ParseException e) {
      String message = e.getMessage();
      log.error("{} - {} Parsing row {}",
          message,
          "DIVI",
          row);
      throw new BusinessException(message);


    }

  }

  @Override
  public boolean isValid(List<String> row) {
    String rate = row.get(fxRate).toUpperCase();
    return rate.contains("."); // divis have an fx rate in this column
  }

  @Override
  public Asset resolveAsset(List<String> row) {
    List<String> values = parseAsset(row.get(code));
    Market market = marketService.getMarket(values.get(1).toUpperCase());
    Asset asset = assetIngestService.resolveAsset(values.get(0), null, market);

    if (!filter.inFilter(asset)) {
      return null;
    }
    return asset;
  }

  private List<String> parseAsset(String input) {
    if (input == null || input.isEmpty()) {
      throw new BusinessException("Unable to resolve Asset code");
    }

    List<String> values = Splitter
        .on(CharMatcher.anyOf(".:-"))
        .trimResults()
        .splitToList(input);

    if (values.isEmpty() || values.get(0).equals(input)) {
      throw new BusinessException(String.format("Unable to parse %s", input));
    }
    return values;
  }

}
