package com.beancounter.client.sharesight;

import com.beancounter.client.ingest.RowAdapter;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.common.identity.TrnId;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
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
  public TrnInput transform(Portfolio portfolio,
                            Asset asset,
                            List<String> values,
                            String provider) {

    TrnAdapter trnAdapter = shareSightFactory.adapter(values);

    if (trnAdapter.isValid(values)) {
      TrnInput trn = trnAdapter.from(values, portfolio, asset);
      if (trn != null) {
        trn.setId(TrnId.builder()
            .provider(provider)
            .build());
        return trn;
      }
    }
    return null;
  }

}
