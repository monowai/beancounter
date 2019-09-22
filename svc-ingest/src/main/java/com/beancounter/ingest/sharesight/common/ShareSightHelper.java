package com.beancounter.ingest.sharesight.common;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.TrnType;
import com.beancounter.ingest.config.ExchangeConfig;
import com.google.api.client.repackaged.com.google.common.base.CharMatcher;
import com.google.api.client.repackaged.com.google.common.base.Splitter;
import com.google.api.client.util.Strings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Helper methods for converting ShareSight google doc output into BC domain objects.
 *
 * @author mikeh
 * @since 2019-02-12
 */
@Service
@Slf4j
public class ShareSightHelper {

  private final ExchangeConfig exchangeConfig;
  private DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");

  @Value("${out.file:#{null}}")
  private String outFile;

  @Value("${range:All Trades Report}")
  private String range;

  @Autowired
  ShareSightHelper(ExchangeConfig exchangeConfig) {
    this.exchangeConfig = exchangeConfig;
  }


  public Date parseDate(String date) throws ParseException {
    return formatter.parse(date);
  }

  public BigDecimal parseDouble(Object o) throws ParseException {
    return new BigDecimal(NumberFormat.getInstance(Locale.US).parse(o.toString()).toString());
  }

  public TrnType resolveType(String type) {
    return TrnType.valueOf(type.toUpperCase());
  }

  /**
   * Split a string that contains both ID and market by various delimiters.
   *
   * @param input "ASSET:MARKET"
   * @return resolvable Asset
   */
  public Asset resolveAsset(String input) {

    if (Strings.isNullOrEmpty(input)) {
      throw new BusinessException("Unable to resolve Asset code");
    }

    List<String> values = Splitter
        .on(CharMatcher.anyOf(".:-"))
        .trimResults()
        .splitToList(input);

    if (values.isEmpty() || values.get(0).equals(input)) {
      throw new BusinessException(String.format("Unable to parse %s", input));
    }

    return Asset.builder()
        .code(values.get(0))
        .market(resolveMarket(values.get(1)))
        .build();
  }

  private Market resolveMarket(String market) {
    return Market.builder()
        .code(exchangeConfig.resolveAlias(market)).build();
  }

  public String getOutFile() {
    return outFile;
  }

  public String getRange() {
    return range;
  }

  public BigDecimal safeDivide(BigDecimal inValue, BigDecimal tradeRate) {
    if (inValue.equals(BigDecimal.ZERO) || tradeRate.equals(BigDecimal.ZERO)) {
      return inValue;
    }
    return inValue.divide(tradeRate, RoundingMode.HALF_UP);
  }
}
