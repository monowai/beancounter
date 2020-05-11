package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.marketdata.assets.AssetEnricher;
import com.beancounter.marketdata.assets.MockEnricher;
import com.beancounter.marketdata.assets.figi.FigiEnricher;
import com.beancounter.marketdata.providers.alpha.AlphaConfig;
import com.beancounter.marketdata.providers.alpha.AlphaEnricher;
import org.junit.jupiter.api.Test;

public class TestEnrichment {

  @Test
  void is_FigiEnrichment() {
    AssetEnricher enricher = new FigiEnricher();
    assertThat(enricher.canEnrich(Asset.builder().build())).isTrue();
    assertThat(enricher.canEnrich(Asset.builder().name("test").build())).isFalse();
  }

  @Test
  void is_MockEnrichment() {
    AssetEnricher enricher = new MockEnricher();
    assertThat(enricher.canEnrich(Asset.builder().build())).isTrue();
    assertThat(enricher.canEnrich(Asset.builder().name("test").build())).isFalse();
  }

  @Test
  void is_AlphaEnrichment() {
    AssetEnricher enricher = new AlphaEnricher(new AlphaConfig());
    assertThat(enricher.canEnrich(Asset.builder().build())).isTrue();
    assertThat(enricher.canEnrich(Asset.builder().name("test").build())).isFalse();
  }

}
