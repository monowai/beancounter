package com.beancounter.marketdata.providers.fxrates;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Api calls to exchangeratesapi.io.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Configuration
@FeignClient(
    name = "fxRequest",
    url = "${beancounter.marketdata.provider.fx.url:https://api.exchangeratesapi.io}")

public interface FxGateway {
  @RequestMapping(
      method = RequestMethod.GET,
      headers = {"Content-Type: text/plain"},
      value = "/{date}?base={base}&symbols={symbols}&"
  )
  EcbRates getRatesForSymbols(
      @PathVariable("date") String date,
      @PathVariable("base") String base,
      @PathVariable("symbols") String symbols);


}
