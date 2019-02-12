package com.beancounter.googled.format;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Transaction;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;


/**
 * Converts from the ShareSight dividend format.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
@Profile("divi")
@Log4j2
public class ShareSightDivis implements Transformer {

  public static final int code = 0;
  public static final int name = 1;
  public static final int date = 2;
  public static final int fxRate = 3;
  public static final int currency = 4;
  public static final int net = 5;
  public static final int tax = 6;
  public static final int gross = 7;
  public static final int comments = 8;

  private ShareSightHelper helper;

  @Value("${out.file:#{systemProperties['user.dir']}/divis.json}")
  private String outFile;


  @Autowired
  public ShareSightDivis(ShareSightHelper helper) {
    this.helper = helper;
  }

  @Override
  public Transaction of(List row) throws ParseException {


    Asset asset = helper.resolveAsset(row.get(code).toString());

    return Transaction.builder()
        .asset(asset)
        .tax(new BigDecimal(row.get(tax).toString()))
        .tradeAmount(helper.parseDouble(row.get(net)))
        .tradeDate(helper.parseDate(row.get(date).toString()))
        .comments(row.get(comments).toString())
        .tradeCurrency(row.get(currency).toString())
        .tradeRate(new BigDecimal(row.get(fxRate).toString()))
        .build()
        ;

  }

  @Override
  public String getFileName() {
    return outFile;
  }


}
