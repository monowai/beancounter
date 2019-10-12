package com.beancounter.ingest.reader;

import com.beancounter.common.model.Transaction;
import com.google.api.client.util.Lists;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Collection;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Filter {

  @Value("${filter:#{null}}")
  private String filter;

  private Collection<String> filteredAssets = new ArrayList<>();

  @PostConstruct
  @VisibleForTesting
  void init() {
    if (filter != null) {
      filteredAssets = Lists.newArrayList(Splitter.on(",").split(filter));
    }
  }

  @VisibleForTesting
  boolean inFilter(Transaction transaction) {
    if (filter != null) {
      return filteredAssets.contains(transaction.getAsset().getCode());
    }
    return true;
  }

  boolean hasFilter() {
    return filter != null;
  }


}
