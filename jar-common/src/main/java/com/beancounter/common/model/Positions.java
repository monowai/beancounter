package com.beancounter.common.model;

import com.beancounter.common.utils.AssetUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NonNull;

/**
 * A container for Position objects.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Data
public class Positions {

  @NonNull
  private Portfolio portfolio;
  private String asAt;
  private Map<String, Position> positions = new HashMap<>();

  Positions() {
    super();
  }

  public Positions(Portfolio portfolio) {
    this();
    this.portfolio = portfolio;
  }

  public void add(Position position) {
    positions.put(AssetUtils.toKey(position.getAsset()), position);

  }

  /**
   * Locate a position for an asset. Creates if missing.
   *
   * @param asset qualified asset
   * @return value if found.
   */
  @JsonIgnore
  public Position get(Asset asset) {
    Position result = positions.get(AssetUtils.toKey(asset));
    if (result != null) {
      return result;
    }
    return Position.builder()
        .asset(asset)
        .build();
  }

}
