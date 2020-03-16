package com.beancounter.shell;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.shell.reader.Filter;
import org.junit.jupiter.api.Test;


class TestFilter {
  @Test
  void is_FilteredAssetsCaseInsensitive() {
    Filter filter = new Filter("Code");
    assertThat(filter.hasFilter()).isTrue();
    assertThat(filter.inFilter(TrnInput.builder()
        .asset(AssetUtils.toKey(getAsset("Code", "Market")))
        .build())).isTrue();

    assertThat(filter.inFilter(TrnInput.builder()
        .asset(AssetUtils.toKey(getAsset("code", "Market")))
        .build())).isTrue();

  }

  @Test
  void is_NotInFilter() {
    Filter filter = new Filter(null);
    assertThat(filter.hasFilter()).isFalse();
  }
}
