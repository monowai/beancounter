package com.beancounter.ingest.reader;

import com.beancounter.common.exception.SystemException;
import com.beancounter.common.identity.TransactionId;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.google.common.annotations.VisibleForTesting;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RowProcessor {

  private ShareSightTransformers shareSightTransformers;
  private Filter filter;

  @Value("${stackTraces:false}")
  private boolean stackTraces = false;

  @Value(("${base.code:USD}"))
  private String baseCurrency;

  @Autowired
  @VisibleForTesting
  void setFilter(Filter filter) {
    this.filter = filter;
  }

  @Autowired
  @VisibleForTesting
  void setShareSightTransformers(ShareSightTransformers shareSightTransformers) {
    this.shareSightTransformers = shareSightTransformers;
  }

  public Collection<Transaction> process(Portfolio portfolio,
                                  List<List<Object>> values,
                                  String provider) {

    Currency systemBase = Currency.builder().code(baseCurrency).build();

    Collection<Transaction> results = new ArrayList<>();
    if (filter.hasFilter()) {
      log.info("Filtering for assets matching {}", filter);
    }

    int trnId = 1;
    for (List row : values) {
      Transformer transformer = shareSightTransformers.transformer(row);

      try {
        if (transformer.isValid(row)) {
          Transaction transaction = transformer.from(row, portfolio, systemBase);

          if (transaction.getId() == null) {
            transaction.setId(TransactionId.builder()
                .id(trnId++)
                .batch(0)
                .provider(provider)
                .build());
          }
          if (filter.inFilter(transaction)) {
            results.add(transaction);
          }
        }
      } catch (ParseException | NumberFormatException e) {
        log.error("{} Parsing row {} - {}", transformer.getClass().getSimpleName(), trnId, row);
        if (stackTraces) {
          throw new SystemException(e.getMessage());
        }
        return results;
      }

    }
    return results;
  }
}
