package com.beancounter.ingest.service;

import com.beancounter.common.model.FxResults;
import com.beancounter.common.request.FxRequest;
import feign.RequestLine;


public interface FxGateway {
  @RequestLine("POST")
  FxResults getRates(FxRequest fxRequest);
}
