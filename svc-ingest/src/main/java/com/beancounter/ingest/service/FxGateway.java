package com.beancounter.ingest.service;

import com.beancounter.common.model.FxResults;
import com.beancounter.common.request.FxRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;


@FeignClient(name = "marketData",
    url = "${marketdata.url:https://localhost:10010/api}")
@Configuration
public interface FxGateway {
  @RequestMapping(method = RequestMethod.POST,
      headers = {
          "Content-Type: application/json"
      },
      value = "/fx"
  )
  FxResults getRates(FxRequest fxRequest);
}
