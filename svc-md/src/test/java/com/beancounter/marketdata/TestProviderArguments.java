package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.marketdata.providers.DataProviderConfig;
import com.beancounter.marketdata.providers.ProviderArguments;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ProviderArguments could get quite complex.  Here are some logic checks to assert various states
 * of being based on batch sizes.
 */
class TestProviderArguments {
  private Asset aapl = AssetUtils.getAsset("AAPL", "NASDAQ");
  private Asset msft = AssetUtils.getAsset("MSFT", "NASDAQ");
  private Asset intc = AssetUtils.getAsset("INTC", "NASDAQ");

  @Test
  void is_BatchOfOne() {

    ProviderArguments providerArguments = new ProviderArguments(new TestConfig(1));

    providerArguments.addAsset("appl", aapl, "");
    providerArguments.addAsset("msft", msft, "");
    providerArguments.addAsset("intc", intc, "");

    Map<Integer, String> batch = providerArguments.getBatch();
    assertThat(batch)
        .containsOnlyKeys(0, 1, 2)
        .containsValue("appl")
        .containsValue("msft")
        .containsValue("intc")
    ;


  }

  @Test
  void is_BatchOfTwo() {
    ProviderArguments providerArguments = new ProviderArguments(new TestConfig(2));
    providerArguments.addAsset("appl", aapl, "");
    providerArguments.addAsset("msft", msft, "");
    providerArguments.addAsset("intc", intc, "");

    Map<Integer, String> batch = providerArguments.getBatch();

    assertThat(batch)
        .containsOnlyKeys(0, 1)
        .containsValue("appl,msft")
        .containsValue("intc")
    ;
  }

  @Test
  void is_BatchOfThree() {
    ProviderArguments providerArguments = new ProviderArguments(new TestConfig(3));
    providerArguments.addAsset("appl", aapl, "");
    providerArguments.addAsset("msft", msft, "");
    providerArguments.addAsset("intc", intc, "");

    Map<Integer, String> batch = providerArguments.getBatch();
    assertThat(batch)
        .containsOnlyKeys(0)
        .containsValue("appl,msft,intc")
    ;
  }

  @Test
  void is_SplitByMarket() {
    Collection<Asset> assets = new ArrayList<>();
    assets.add(AssetUtils.getAsset("ABC", "AAA"));
    assets.add(AssetUtils.getAsset("ABC", "BBB"));
    assets.add(AssetUtils.getAsset("ABC", "CCC"));
    PriceRequest priceRequest = PriceRequest.builder().assets(assets).build();
    TestConfig testConfig = new TestConfig(10);
    ProviderArguments providerArguments =
        ProviderArguments.getInstance(priceRequest, testConfig);
    Map<Integer, String> batch = providerArguments.getBatch();

    assertThat(batch)
        .containsOnlyKeys(0, 1, 2);

  }


  private static class TestConfig implements DataProviderConfig {
    private Integer batchSize;

    TestConfig(Integer batchSize) {
      this.batchSize = batchSize;
    }

    @Override
    public Integer getBatchSize() {
      return batchSize;
    }

    @Override
    public String translateMarketCode(Market market) {
      return null;
    }

    @Override
    public String getMarketDate(Market market, String date) {
      return date;
    }

  }
}
