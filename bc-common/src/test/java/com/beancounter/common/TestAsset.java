package com.beancounter.common;

import com.beancounter.common.model.Asset;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author mikeh
 * @since 2019-01-30
 */
class TestAsset {
    @Test
    void jsonSerialization() throws Exception {
        
        Asset asset = Asset.builder()
            .id("SomeId")
            .build();

        assertThat(asset).isNotNull();

        ObjectMapper om = new ObjectMapper();
        String json = om.writeValueAsString(asset);

        Asset fromJson = om.readValue(json, Asset.class);

        assertThat(fromJson).isEqualTo(asset);
    }
}
