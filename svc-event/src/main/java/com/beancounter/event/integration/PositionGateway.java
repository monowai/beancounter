package com.beancounter.event.integration;


import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.input.TrustedTrnQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Configuration
@FeignClient(
    name = "bcPosition",
    url = "${position.url:http://localhost:9500/api}")
public interface PositionGateway {
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/query",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  PositionResponse query(TrustedTrnQuery trnQuery);

}
