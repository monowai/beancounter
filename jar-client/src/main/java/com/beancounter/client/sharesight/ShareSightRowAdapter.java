package com.beancounter.client.sharesight;

import com.beancounter.client.ingest.RowAdapter;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnImportRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ShareSightRowAdapter implements RowAdapter {

  private ShareSightFactory shareSightFactory;

  @Autowired
  void setShareSightFactory(ShareSightFactory shareSightFactory) {
    this.shareSightFactory = shareSightFactory;
  }

  @Override
  public TrnInput transform(TrustedTrnImportRequest trnRequest) {

    TrnAdapter trnAdapter = shareSightFactory.adapter(trnRequest.getRow());

    if (trnAdapter.isValid(trnRequest.getRow())) {
      return trnAdapter.from(trnRequest);
    }
    return null;
  }

}
