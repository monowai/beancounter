package com.beancounter.shell.sharesight;

import com.beancounter.shell.reader.Transformer;
import java.util.List;
import java.util.Set;
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

  private static final Set<String> TTYPES = Set.of("buy", "sell", "split");
  private ShareSightTrades shareSightTrades;
  private ShareSightDivis shareSightDivis;

  @Autowired
  public ShareSightTransformers(ShareSightDivis shareSightDivis,
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
  public Transformer transformer(List<String> row) {
    String ttype = row.get(ShareSightTrades.type).toLowerCase();
    if (TTYPES.contains(ttype)) {
      return shareSightTrades;
    }
    return shareSightDivis;
  }

}
