package com.beancounter.client;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;

public interface FxService {

  FxResponse getRates(FxRequest fxRequest);
}
