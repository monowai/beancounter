package com.beancounter.client.sharesight;

import com.beancounter.client.ingest.RowAdapter;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.common.identity.CallerRef;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
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
  public TrnInput transform(TrustedTrnRequest trnRequest) {

    TrnAdapter trnAdapter = shareSightFactory.adapter(trnRequest.getRow());

    if (trnAdapter.isValid(trnRequest.getRow())) {
      TrnInput trn = trnAdapter.from(trnRequest);
      if (trn != null) {
        trn.setCallerRef(CallerRef.from(trnRequest.getCallerRef()));
        return trn;
      }
    }
    return null;
  }

}
