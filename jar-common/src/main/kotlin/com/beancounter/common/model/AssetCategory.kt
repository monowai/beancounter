package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * Simple classification structure of an Asset.
 */
data class AssetCategory @ConstructorBinding constructor(var id: String, var name: String) {
    @JsonIgnore
    fun isCash(): Boolean {
        return id == "CASH"
    }
}
