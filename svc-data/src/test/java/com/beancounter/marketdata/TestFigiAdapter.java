package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.assets.figi.FigiAdapter;
import com.beancounter.marketdata.assets.figi.FigiAsset;
import org.junit.jupiter.api.Test;

public class TestFigiAdapter {
  @Test
  void is_CodePreserved() {
    FigiAdapter figiAdapter = new FigiAdapter();
    FigiAsset figiAsset = new FigiAsset();
    figiAsset.setTicker("BRK/B");
    figiAsset.setName("BRK");
    figiAsset.setSecurityType2("Mutual Fund");
    Asset asset = figiAdapter.transform(
        Market.builder().code("TEST").build(),
        "BRK.B",
        figiAsset
    );

    assertThat(asset)
        .hasFieldOrPropertyWithValue("name", "BRK")
        .hasFieldOrPropertyWithValue("code", "BRK.B")
        .hasFieldOrPropertyWithValue("category", "Mutual Fund");
  }
}
