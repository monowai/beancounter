package com.beancounter.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.ingest.Filter;
import com.beancounter.common.utils.AssetUtils;
import org.junit.jupiter.api.Test;


class TestFilter {
  @Test
  void is_FilteredAssetsCaseInsensitive() {
    Filter filter = new Filter("Code");
    assertThat(filter.hasFilter()).isTrue();
    assertThat(filter.inFilter(AssetUtils.getAsset("Market", "Code")))
        .isTrue();

    assertThat(filter.inFilter(AssetUtils.getAsset("Market", "code")))
        .isTrue();

  }

  @Test
  void is_NotInFilter() {
    Filter filter = new Filter(null);
    assertThat(filter.hasFilter()).isFalse();
  }
}
