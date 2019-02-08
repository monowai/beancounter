package com.beancounter.marketdata.integration;

import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @author mikeh
 * @since 2019-01-28
 */
@Configuration
@Data
public class AlphaClient {
     private String apiKey;

//     public interface Api {
//         @RequestMapping(method = RequestMethod.GET, value = "/stores")
//
//
//     }
}
