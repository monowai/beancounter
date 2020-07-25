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
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class ShareSightDividendAdapter implements TrnAdapter {
  public static final int id = 0;
  public static final int code = 1;
  public static final int name = 2;
  public static final int date = 3;
  public static final int fxRate = 4;
  public static final int currency = 5;
  public static final int net = 6;
  public static final int tax = 7;
  public static final int gross = 8;
  public static final int comments = 9;
  private final ShareSightConfig shareSightConfig;
  private final DateUtils dateUtils = new DateUtils();
  private final AssetIngestService assetIngestService;
  private Filter filter = new Filter(null);
  private static final Logger log = LoggerFactory.getLogger(ShareSightDividendAdapter.class);

  public ShareSightDividendAdapter(ShareSightConfig shareSightConfig,
                                   AssetIngestService assetIngestService) {
    this.shareSightConfig = shareSightConfig;
    this.assetIngestService = assetIngestService;
  }

  @Autowired(required = false)
  void setFilter(Filter filter) {
    this.filter = filter;
  }

  @Override
  public TrnInput from(TrustedTrnImportRequest trustedTrnImportRequest) {
    assert trustedTrnImportRequest != null;
    List<String> row = trustedTrnImportRequest.getRow();
    try {
      assert row != null;
      Asset asset = resolveAsset(row);
      if (asset == null) {
        log.error("Unable to resolve asset [{}]", row);
        return null;
      }

      BigDecimal tradeRate = MathUtils.parse(row.get(fxRate), shareSightConfig.getNumberFormat());
      TrnInput trnInput = new TrnInput(
          new CallerRef(trustedTrnImportRequest.getPortfolio().getId(), null, row.get(id)),
          Objects.requireNonNull(asset.getId()),
          TrnType.DIVI, BigDecimal.ZERO
      );

      trnInput.setTradeCurrency(row.get(currency));
      trnInput.setTax(MathUtils.multiply(new BigDecimal(row.get(tax)), tradeRate));
      trnInput.setTradeAmount(
          MathUtils.multiply(
              MathUtils.parse(row.get(net), shareSightConfig.getNumberFormat()),
              tradeRate));
      trnInput.setCashAmount(MathUtils.multiply(
          MathUtils.parse(row.get(net), shareSightConfig.getNumberFormat()),
          tradeRate));
      trnInput.setTradeDate(dateUtils.getDate(row.get(date),
          shareSightConfig.getDateFormat(),
          dateUtils.getZoneId()));
      trnInput.setComments(row.get(comments));
      trnInput.setTradeCashRate(shareSightConfig.isCalculateRates() || MathUtils.isUnset(tradeRate)
          ? null : tradeRate);
      return trnInput;
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
    Asset asset = assetIngestService.resolveAsset(
        values.get(1).toUpperCase(), values.get(0)
    );

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
