package com.beancounter.position.model;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * A container for Position objects.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Data
public class Positions {

  @NotNull
  private Portfolio portfolio;
  private Map<String, Position> positions = new HashMap<>();

  Positions() {
    super();
  }

  public Positions(Portfolio portfolio) {
    this();
    this.portfolio = portfolio;
  }

  public void add(Position position) {
    positions.put(parseKey(position.getAsset()), position);

  }

  /**
   * Locate a position for an asset. Creates if missing.
   *
   * @param asset qualified asset
   * @return value if found.
   */
  @JsonIgnore
  public Position get(Asset asset) {
    Position result = positions.get(parseKey(asset));
    if (result == null) {
      return Position.builder()
          .asset(asset)
          .build();
    }
    return result;
  }

  @JsonIgnore
  private String parseKey(@NotNull Asset asset) {
    assert asset.getMarket() != null;
    return asset.getCode() + ":" + asset.getMarket().getCode();
  }

}
