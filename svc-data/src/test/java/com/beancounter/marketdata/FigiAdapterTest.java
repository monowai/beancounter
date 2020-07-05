package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.assets.figi.FigiAdapter;
import com.beancounter.marketdata.assets.figi.FigiAsset;
import org.junit.jupiter.api.Test;

public class FigiAdapterTest {
  @Test
  void is_CodePreserved() {
    FigiAdapter figiAdapter = new FigiAdapter();
    FigiAsset figiAsset = new FigiAsset("BRK", "BRK/B", "Mutual Fund");
    Asset asset = figiAdapter.transform(
        new Market("TEST", new Currency("USD")),
        "BRK.B",
        figiAsset
    );

    assertThat(asset)
        .hasFieldOrPropertyWithValue("name", "BRK")
        .hasFieldOrPropertyWithValue("code", "BRK.B")
        .hasFieldOrPropertyWithValue("category", "Mutual Fund");
  }
}
