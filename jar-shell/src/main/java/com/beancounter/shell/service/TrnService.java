package com.beancounter.shell.service;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

@Service
public class TrnService {
  private TrnGateway trnGateway;

  TrnService(TrnGateway trnGateway) {
    this.trnGateway = trnGateway;
  }

  public TrnResponse write(TrnRequest trnRequest) {
    return trnGateway.write(trnRequest);
  }

  @FeignClient(name = "trns",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface TrnGateway {
    @PostMapping(value = "/trns",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    TrnResponse write(TrnRequest trnRequest);


  }

}
