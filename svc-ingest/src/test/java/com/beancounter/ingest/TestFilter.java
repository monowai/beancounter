package com.beancounter.ingest;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Trn;
import com.beancounter.ingest.reader.Filter;
import org.junit.jupiter.api.Test;


class TestFilter {
  @Test
  void is_FilteredAssetsCaseInsensitive() {
    Filter filter = new Filter("Code");
    assertThat(filter.hasFilter()).isTrue();
    assertThat(filter.inFilter(Trn.builder()
        .asset(getAsset("Code", "Market"))
        .build())).isTrue();

    assertThat(filter.inFilter(Trn.builder()
        .asset(getAsset("code", "Market"))
        .build())).isTrue();

  }

  @Test
  void is_NotInFilter() {
    Filter filter = new Filter(null);
    assertThat(filter.hasFilter()).isFalse();
  }
}
