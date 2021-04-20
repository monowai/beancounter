package com.beancounter.common.model;

import static com.beancounter.common.utils.AssetKeyUtils.toKey;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A container for Position objects.
 *
 * @author mikeh
 * @since 2019-02-07
 */
public class Positions {

  private Portfolio portfolio;
  private String asAt;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, Position> positions = null;
  private Map<Position.In, Totals> totals = null;

  Positions() {
    super();
  }

  public Positions(Portfolio portfolio) {
    this();
    this.portfolio = portfolio;
  }

  public void add(Position position) {
    if (positions == null) {
      positions = new TreeMap<>();
    }
    positions.put(toKey(position.getAsset()), position);

  }

  /**
   * Locate a position for an asset. Creates if missing.
   *
   * @param asset qualified asset
   * @return value if found.
   */
  @JsonIgnore
  public Position get(Asset asset) {
    if (positions == null) {
      positions = new TreeMap<>();
    }

    Position result = positions.get(toKey(asset));
    if (result != null) {
      return result;
    }
    return new Position(asset);
  }

  @JsonIgnore
  public Position get(Asset asset, LocalDate tradeDate) {
    if (positions == null) {
      positions = new TreeMap<>();
    }
    boolean firstTrade = !positions.containsKey(toKey(asset));
    Position position = get(asset);
    if (firstTrade) {
      position.getDateValues().setOpened(tradeDate);
    }
    return position;
  }

  public boolean hasPositions() {
    return positions != null && !positions.isEmpty();
  }

  public void setTotal(Position.In valueIn, Totals totals) {
    if (this.totals == null) {
      this.totals = new HashMap<>();
    }
    this.totals.put(valueIn, totals);
  }

  public Portfolio getPortfolio() {
    return this.portfolio;
  }

  public String getAsAt() {
    return this.asAt;
  }

  public Map<String, Position> getPositions() {
    return this.positions;
  }

  public Map<Position.In, Totals> getTotals() {
    return this.totals;
  }

  public void setPortfolio(Portfolio portfolio) {
    this.portfolio = portfolio;
  }

  public void setAsAt(String asAt) {
    this.asAt = asAt;
  }

  public void setPositions(Map<String, Position> positions) {
    this.positions = positions;
  }

}
