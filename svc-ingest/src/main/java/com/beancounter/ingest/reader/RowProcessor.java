package com.beancounter.ingest.reader;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.identity.TransactionId;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
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

  public Collection<Transaction> transform(Portfolio portfolio,
                                           List<List<Object>> values,
                                           String provider) {
    return transform(portfolio, values, new Filter(null), provider);
  }

  public Collection<Transaction> transform(Portfolio portfolio,
                                           List<List<Object>> values,
                                           Filter filter,
                                           String provider) {

    Collection<Transaction> results = new ArrayList<>();
    if (filter.hasFilter()) {
      log.info("Filtering for assets matching {}", filter);
    }

    int trnId = 1;
    for (List row : values) {
      Transformer transformer = shareSightTransformers.transformer(row);

      try {
        if (transformer.isValid(row)) {
          Transaction transaction = transformer.from(row, portfolio);

          transaction.setId(TransactionId.builder()
              .id(trnId++)
              .batch(0)
              .provider(provider)
              .build());
          if (filter.inFilter(transaction)) {
            results.add(transaction);
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
