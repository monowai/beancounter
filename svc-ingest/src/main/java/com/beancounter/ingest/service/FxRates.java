package com.beancounter.ingest.service;

import com.beancounter.common.model.FxResults;
import com.beancounter.common.request.FxRequest;
import feign.Feign;
import feign.Logger;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import feign.okhttp.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FxRates {

  private FxGateway fxGateway;

  @Bean
  FxGateway feignFxGateway(){
    this.fxGateway= Feign.builder()
        .client(new OkHttpClient())
        .encoder(new GsonEncoder())
        .decoder(new GsonDecoder())
        .logger(new Slf4jLogger(FxGateway.class))
        .logLevel(Logger.Level.FULL)
        .target(FxGateway.class, "http://localhost:10010/fx");
    return fxGateway;
  }

  FxResults getRates(FxRequest fxRequest ) {
    return fxGateway.getRates(fxRequest);
  }

}
