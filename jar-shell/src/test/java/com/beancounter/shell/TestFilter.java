package com.beancounter.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.utils.AssetUtils;
import com.beancounter.shell.ingest.Filter;
import org.junit.jupiter.api.Test;


class TestFilter {
  @Test
  void is_FilteredAssetsCaseInsensitive() {
    Filter filter = new Filter("Code");
    assertThat(filter.hasFilter()).isTrue();
    assertThat(filter.inFilter(AssetUtils.getAsset("Code", "Market")))
        .isTrue();

    assertThat(filter.inFilter(AssetUtils.getAsset("code", "Market")))
        .isTrue();

  }

  @Test
  void is_NotInFilter() {
    Filter filter = new Filter(null);
    assertThat(filter.hasFilter()).isFalse();
  }
}
