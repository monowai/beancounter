package com.beancounter.marketdata.assets;

import com.beancounter.common.model.Asset;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AssetRepository extends CrudRepository<Asset, String> {
  Optional<Asset> findByMarketCodeAndCode(String marketCode, String code);
  @Query("select a from Asset a")
  @Transactional(readOnly = true)
  Stream<Asset> findAllAssets();
}
