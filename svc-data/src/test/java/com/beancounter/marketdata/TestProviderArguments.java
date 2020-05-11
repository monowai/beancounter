package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.providers.DataProviderConfig;
import com.beancounter.marketdata.providers.ProviderArguments;
import com.beancounter.marketdata.providers.ProviderUtils;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.service.MdFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ProviderArguments could get quite complex.  Here are some logic checks to assert various states
 * of being based on batch sizes.
 */
class TestProviderArguments {
  private final Asset aapl = AssetUtils.getAsset("NASDAQ", "AAPL");
  private final Asset msft = AssetUtils.getAsset("NASDAQ", "MSFT");
  private final Asset intc = AssetUtils.getAsset("NASDAQ", "INTC");

  @Test
  void is_BatchOfOne() {

    ProviderArguments providerArguments = new ProviderArguments(new TestConfig(1));

    providerArguments.addAsset(aapl, "");
    providerArguments.addAsset(msft, "");
    providerArguments.addAsset(intc, "");

    Map<Integer, String> batch = providerArguments.getBatch();
    assertThat(batch)
        .containsOnlyKeys(0, 1, 2)
        .containsValues("AAPL", "MSFT", "INTC")
    ;


  }

  @Test
  void is_BatchOfTwo() {
    ProviderArguments providerArguments = new ProviderArguments(new TestConfig(2));
    providerArguments.addAsset(aapl, "");
    providerArguments.addAsset(msft, "");
    providerArguments.addAsset(intc, "");

    Map<Integer, String> batch = providerArguments.getBatch();

    assertThat(batch)
        .containsOnlyKeys(0, 1)
        .containsValue("AAPL,MSFT")
        .containsValue("INTC")
    ;
  }

  @Test
  void is_BatchOfThree() {
    ProviderArguments providerArguments = new ProviderArguments(new TestConfig(3));
    providerArguments.addAsset(aapl, "");
    providerArguments.addAsset(msft, "");
    providerArguments.addAsset(intc, "");

    Map<Integer, String> batch = providerArguments.getBatch();
    assertThat(batch)
        .containsOnlyKeys(0)
        .containsValue("AAPL,MSFT,INTC")
    ;
  }

  @Test
  void is_SplitByMarket() {
    Collection<AssetInput> assets = new ArrayList<>();
    assets.add(AssetInput.builder()
        .resolvedAsset(AssetUtils
            .getAsset("AAA", "ABC")).build());

    assets.add(AssetInput.builder()
        .resolvedAsset(AssetUtils
            .getAsset("BBB", "ABC")).build());

    assets.add(AssetInput.builder()
        .resolvedAsset(AssetUtils
            .getAsset("CCC", "ABC")).build());

    PriceRequest priceRequest = PriceRequest.builder().assets(assets).build();
    TestConfig testConfig = new TestConfig(10);
    ProviderArguments providerArguments =
        ProviderArguments.getInstance(priceRequest, testConfig);
    Map<Integer, String> batch = providerArguments.getBatch();

    assertThat(batch)
        .containsOnlyKeys(0, 1, 2);

  }

  @Test
  void is_ProviderUtils() {
    Collection<AssetInput> assetInputs = new ArrayList<>();

    assetInputs.add(AssetInput.builder()
        .code("TWEE")
        .market("MOCK")
        .build());

    ProviderUtils providerUtils = new ProviderUtils(mock(MdFactory.class));
    Map<MarketDataProvider, Collection<Asset>> split = providerUtils.splitProviders(assetInputs);
    assertThat(split).hasSize(1);
    for (MarketDataProvider marketDataProvider : split.keySet()) {
      assertThat(split.get(marketDataProvider)).hasSize(1);
    }

  }


  private static class TestConfig implements DataProviderConfig {
    private final Integer batchSize;

    TestConfig(Integer batchSize) {
      this.batchSize = batchSize;
    }

    @Override
    public Integer getBatchSize() {
      return batchSize;
    }

    @Override
    public LocalDate getMarketDate(Market market, String date) {
      return new DateUtils().getDate(date);
    }

    @Override
    public String getPriceCode(Asset asset) {
      return asset.getCode();
    }

  }
}
