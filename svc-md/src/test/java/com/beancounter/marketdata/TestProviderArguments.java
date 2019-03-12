package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.providers.ProviderArguments;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ProviderArguments could get quite complex.  Here are some logic checks to assert various states
 * of being based on batch sizes.
 * 
 */
class TestProviderArguments {

  @Test
  void batcheOfOne() {

    Asset aapl =
        Asset.builder().code("AAPL").market(Market.builder().code("NASDAQ").build()).build();
    Asset msft =
        Asset.builder().code("MSFT").market(Market.builder().code("NASDAQ").build()).build();
    Asset intc =
        Asset.builder().code("INTC").market(Market.builder().code("NASDAQ").build()).build();


    ProviderArguments providerArguments = new ProviderArguments(1);

    providerArguments.addAsset(aapl, "appl");
    providerArguments.addAsset(msft, "msft");
    providerArguments.addAsset(intc, "intc");

    Map<Integer, String> batch = providerArguments.getBatch();
    assertThat(batch)
        .containsOnlyKeys(0, 1, 2)
        .containsValue("appl")
        .containsValue("msft")
        .containsValue("intc")
    ;


  }

  @Test
  void batcheOfTwo() {

    Asset aapl =
        Asset.builder().code("AAPL").market(Market.builder().code("NASDAQ").build()).build();
    Asset msft =
        Asset.builder().code("MSFT").market(Market.builder().code("NASDAQ").build()).build();
    Asset intc =
        Asset.builder().code("INTC").market(Market.builder().code("NASDAQ").build()).build();


    ProviderArguments providerArguments = new ProviderArguments(2);

    providerArguments.addAsset(aapl, "appl");
    providerArguments.addAsset(msft, "msft");
    providerArguments.addAsset(intc, "intc");

    Map<Integer, String> batch = providerArguments.getBatch();

    assertThat(batch)
        .containsOnlyKeys(0, 1)
        .containsValue("appl,msft")
        .containsValue("intc")
    ;


  }

  @Test
  void batcheOfThree() {

    Asset aapl =
        Asset.builder().code("AAPL").market(Market.builder().code("NASDAQ").build()).build();
    Asset msft =
        Asset.builder().code("MSFT").market(Market.builder().code("NASDAQ").build()).build();
    Asset intc =
        Asset.builder().code("INTC").market(Market.builder().code("NASDAQ").build()).build();


    ProviderArguments providerArguments = new ProviderArguments(3);

    providerArguments.addAsset(aapl, "appl");
    providerArguments.addAsset(msft, "msft");
    providerArguments.addAsset(intc, "intc");

    Map<Integer, String> batch = providerArguments.getBatch();
    assertThat(batch)
        .containsOnlyKeys(0)
        .containsValue("appl,msft,intc")
    ;


  }

}
