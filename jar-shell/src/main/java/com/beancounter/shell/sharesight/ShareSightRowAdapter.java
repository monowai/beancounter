package com.beancounter.shell.sharesight;

import com.beancounter.common.identity.TrnId;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.shell.ingest.RowAdapter;
import com.beancounter.shell.ingest.TrnAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ShareSightRowAdapter implements RowAdapter {

  private ShareSightFactory shareSightFactory;

  @Autowired
  void setShareSightFactory(ShareSightFactory shareSightFactory) {
    this.shareSightFactory = shareSightFactory;
  }

  @Override
  public Collection<TrnInput> transform(Portfolio portfolio,
                                        List<List<String>> values,
                                        String provider) {

    Collection<TrnInput> results = new ArrayList<>();

    for (List<String> row : values) {
      TrnAdapter trnAdapter = shareSightFactory.adapter(row);

      if (trnAdapter.isValid(row)) {
        TrnInput trn = trnAdapter.from(row, portfolio);
        if (trn != null) {
          trn.setId(TrnId.builder()
              .provider(provider)
              .build());
          results.add(trn);
        }
      }
    }
    return results;
  }

}
