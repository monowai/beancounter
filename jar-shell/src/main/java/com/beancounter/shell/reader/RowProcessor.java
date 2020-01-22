package com.beancounter.shell.reader;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.identity.TrnId;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.shell.sharesight.ShareSightTransformers;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RowProcessor {

  private ShareSightTransformers shareSightTransformers;

  @Autowired
  void setShareSightTransformers(ShareSightTransformers shareSightTransformers) {
    this.shareSightTransformers = shareSightTransformers;
  }

  public Collection<Trn> transform(Portfolio portfolio,
                                   List<List<Object>> values,
                                   String provider) {
    return transform(portfolio, values, new Filter(null), provider);
  }

  public Collection<Trn> transform(Portfolio portfolio,
                                   List<List<Object>> values,
                                   Filter filter,
                                   String provider) {

    Collection<Trn> results = new ArrayList<>();
    if (filter.hasFilter()) {
      log.info("Filtering for assets matching {}", filter);
    }

    int trnId = 1;
    for (List row : values) {
      Transformer transformer = shareSightTransformers.transformer(row);

      try {
        if (transformer.isValid(row)) {
          Trn trn = transformer.from(row, portfolio);

          trn.setId(TrnId.builder()
              .id(trnId++)
              .batch(0)
              .provider(provider)
              .build());
          if (filter.inFilter(trn)) {
            results.add(trn);
          }
        }
      } catch (ParseException | NumberFormatException e) {
        log.error("{} Parsing row {} - {}", transformer.getClass().getSimpleName(), trnId, row);
        throw new BusinessException(e.getMessage());
      }

    }
    return results;
  }

}
