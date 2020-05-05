package com.beancounter.common.model;

import com.beancounter.common.utils.AssetUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import lombok.Data;
import lombok.NonNull;

/**
 * A container for Position objects.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Data()
public class Positions {

  @NonNull
  private Portfolio portfolio;
  private String asAt;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, Position> positions = null;

  Positions() {
    super();
  }

  public Positions(Portfolio portfolio) {
    this();
    this.portfolio = portfolio;
  }

  public void add(Position position) {
    if ( positions == null ) {
      positions = new TreeMap<>();
    }
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
    if ( positions == null ) {
      positions = new TreeMap<>();
    }

    Position result = positions.get(AssetUtils.toKey(asset));
    if (result != null) {
      return result;
    }
    return Position.builder()
        .asset(asset)
        .build();
  }

  @JsonIgnore
  public Position get(Asset asset, LocalDate tradeDate) {
    if ( positions == null ) {
      positions = new TreeMap<>();
    }
    boolean firstTrade = !positions.containsKey(AssetUtils.toKey(asset));
    Position position = get(asset);
    if (firstTrade) {
      position.getDateValues().setOpened(tradeDate.toString());
    }
    return position;
  }

  public boolean hasPositions() {
    return positions != null && !positions.isEmpty();
  }
}
