package com.beancounter.shell.reader;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.utils.AssetUtils;
import com.google.api.client.util.Lists;
import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;

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

  public boolean inFilter(TrnInput trn) {
    if (!filteredAssets.isEmpty()) {
      return filteredAssets.contains(AssetUtils.fromKey(trn.getAsset()).getCode().toUpperCase());
    }
    return true;
  }

  public boolean hasFilter() {
    return !filteredAssets.isEmpty();
  }


}
