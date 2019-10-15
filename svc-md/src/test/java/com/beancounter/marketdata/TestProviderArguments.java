package com.beancounter.marketdata;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.marketdata.providers.ProviderArguments;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ProviderArguments could get quite complex.  Here are some logic checks to assert various states
 * of being based on batch sizes.
 */
class TestProviderArguments {
  private Asset aapl = getAsset("AAPL", "NASDAQ");
  private Asset msft = getAsset("MSFT", "NASDAQ");
  private Asset intc = getAsset("INTC", "NASDAQ");

  @Test
  void batchOfOne() {

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
  void batchOfTwo() {


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
  void batchOfThree() {

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
