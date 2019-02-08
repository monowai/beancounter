package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;

/**
 * @author mikeh
 * @since 2019-01-27
 */
@Data
@Builder
@JsonDeserialize(builder = Market.ExchangeBuilder.class)
public class Market {
    String id;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class ExchangeBuilder {
    }

}
