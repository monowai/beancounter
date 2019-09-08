package com.beancounter.googled.sharesight;

import com.beancounter.googled.reader.Transformer;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Factory for getting the appropriate row transformer.
 *
 * @author mikeh
 * @since 2019-03-10
 */
@Service
public class ShareSightTransformers {

  private ShareSightTrades shareSightTrades;
  private ShareSightDivis shareSightDivis;

  @Autowired
  ShareSightTransformers(ShareSightDivis shareSightDivis,
                         ShareSightTrades shareSightTrades) {
    this.shareSightDivis = shareSightDivis;
    this.shareSightTrades = shareSightTrades;
  }

  /**
   * Figure out if we're dealing with a Trade or Dividend row.
   *
   * @param row analyze this
   * @return appropriate transformer
   */
  public Transformer transformer(List row) {
    if (row.size() == 9) {
      if (row.get(ShareSightTrades.type).toString().equalsIgnoreCase("split")) {
        return shareSightTrades;
      }
      return shareSightDivis;
    }
    return shareSightTrades;
  }

  /**
   * Helper to return a transformer of an explicit type.
   *
   * @param type DIVI/TRADE
   * @return DIVI otherwise TRADE
   */
  public Transformer transformer(String type) {
    if (type.equalsIgnoreCase("DIVI")) {
      return shareSightDivis;
    } else {
      return shareSightTrades;
    }
  }

}
