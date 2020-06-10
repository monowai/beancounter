package com.beancounter.client.ingest;

import com.beancounter.common.model.Asset;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Filter {

  private final Collection<String> filteredAssets = new ArrayList<>();

  public Filter(@Value("${filter:#{null}}") String filter) {
    init(filter);
  }

  private void init(String filter) {
    if (filter != null) {
      String[] values = filter.split(",");
      for (String value : values) {
        filteredAssets.add(value.toUpperCase());
      }
    }
  }

  public boolean inFilter(Asset asset) {
    if (!filteredAssets.isEmpty()) {
      return filteredAssets.contains(asset.getCode().toUpperCase());
    }
    return true;
  }

  public boolean hasFilter() {
    return !filteredAssets.isEmpty();
  }


}
