package com.beancounter.common.model;

import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * A container for Position objects.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Data()
@EqualsAndHashCode(exclude = "dateUtils")
public class Positions {

  @NonNull
  private Portfolio portfolio;
  private String asAt;
  private Map<String, Position> positions = new HashMap<>();
  @Getter(AccessLevel.PRIVATE)
  @Setter(AccessLevel.PRIVATE)
  @Builder.Default
  @JsonIgnore
  private DateUtils dateUtils = new DateUtils();

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

  @JsonIgnore
  public Position get(Transaction transaction) {
    boolean firstTrade = !positions.containsKey(AssetUtils.toKey(transaction.getAsset()));
    Position position = get(transaction.getAsset());
    if (firstTrade) {
      position.getDateValues().setOpened(dateUtils.getDate(transaction.getTradeDate()));
    }
    return position;
  }
}
