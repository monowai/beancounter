package com.beancounter.marketdata.assets;

import com.beancounter.common.model.Asset;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface AssetRepository extends CrudRepository<Asset, String> {
  Optional<Asset> findByMarketCodeAndCode(String marketCode, String code);
}
