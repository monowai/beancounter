package com.beancounter.ingest.reader;

import com.beancounter.common.model.Transaction;
import com.google.api.client.util.Lists;
import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Filter {

  private Collection<String> filteredAssets = new ArrayList<>();


  public Filter(@Value("${filter:#{null}}") String filter) {
    init(filter);
  }

  private void init(String filter) {
    if (filter != null) {
      filteredAssets = Lists.newArrayList(Splitter.on(",").split(filter.toUpperCase()));
    }
  }

  public boolean inFilter(Transaction transaction) {
    if (!filteredAssets.isEmpty()) {
      return filteredAssets.contains(transaction.getAsset().getCode().toUpperCase());
    }
    return true;
  }

  public boolean hasFilter() {
    return !filteredAssets.isEmpty();
  }


}
