package com.beancounter.client;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

@Service
@Slf4j
public class PriceService {

  private PriceGateway priceGateway;

  @Autowired
  PriceService(PriceGateway priceGateway) {
    this.priceGateway = priceGateway;
  }

  public PriceResponse getPrices(PriceRequest priceRequest) {
    return priceGateway.getPrices(priceRequest);
  }

  @FeignClient(name = "prices",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface PriceGateway {
    @GetMapping(
        value = "/prices",
        produces = {MediaType.APPLICATION_JSON_VALUE})
    PriceResponse getPrices(PriceRequest priceRequest);

  }

}
