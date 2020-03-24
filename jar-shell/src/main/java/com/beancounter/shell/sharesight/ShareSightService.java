package com.beancounter.shell.sharesight;

import com.beancounter.client.AssetService;
import com.beancounter.client.StaticService;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.shell.ingest.Filter;
import com.google.api.client.util.Strings;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Helper methods for converting ShareSight file format into BC domain objects.
 *
 * @author mikeh
 * @since 2019-02-12
 */
@Service
@Slf4j
@Data
public class ShareSightService {

  @Value("${ratesIgnored:false}")
  private boolean ratesIgnored = false; // Use rates in source file to compute values, but have BC
  private Filter filter;
  private AssetService assetService;
  private DateUtils dateUtils = new DateUtils();
  private StaticService staticService;
  private NumberFormat numberFormatter = NumberFormat.getInstance(Locale.US);

  // retrieve rates from market data service
  @Autowired
  public ShareSightService(AssetService assetService, StaticService staticService) {
    this.assetService = assetService;
    this.staticService = staticService;
  }

  @Autowired
  void setDateUtils(DateUtils dateUtils) {
    this.dateUtils = dateUtils;
  }


  public LocalDate parseDate(Object date) {
    if (date == null) {
      return null;
    }
    return dateUtils.getDate(date.toString(), "dd/MM/yyyy");
  }

  public BigDecimal parseDouble(Object o) throws ParseException {
    try {
      if (o == null) {
        return null;
      }
      if (o.toString().isBlank()) {
        return BigDecimal.ZERO;
      }

      return new BigDecimal(numberFormatter.parse(o.toString()).toString());
    } catch (ParseException e) {
      log.error("Unable to parse {}", o);
      throw e;
    }
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

    return resolveAsset(values.get(0), null, values.get(1));
  }

  public Asset resolveAsset(String assetCode, String assetName, String marketCode) {
    return assetService.resolveAsset(assetCode, assetName, staticService.resolveMarket(marketCode));
  }

  BigDecimal safeDivide(BigDecimal money, BigDecimal rate) {
    return MathUtils.divide(money, rate);
  }

  BigDecimal getValueWithFx(BigDecimal money, BigDecimal rate) {
    return MathUtils.multiply(money, rate);
  }

  boolean isUnset(BigDecimal value) {
    return MathUtils.isUnset(value);
  }

  public boolean inFilter(Asset asset) {
    return filter == null || filter.inFilter(asset);
  }
}
