package com.beancounter.position.model;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mikeh
 * @since 2019-02-07
 */
public class Positions {

    @NotNull
    private Portfolio portfolio;
    private Map<String, Position> positions = new HashMap<>();

    public Positions(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public void add(Position position) {
        positions.put(parseKey(position.getAsset()), position);

    }

    public Position get(Asset asset) {
        Position result = positions.get(parseKey(asset));
        if (result == null) {
            return Position.builder()
                .asset(asset)
                .build();
        }
        return result;
    }

    private String parseKey(@NotNull Asset asset) {
        assert asset.getMarket() != null;
        return asset.getMarket().getId()+":"+asset.getId();
    }

}
