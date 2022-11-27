package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Simple classification structure of an Asset.
 */
data class AssetCategory constructor(var id: String, var name: String) {
    @JsonIgnore
    fun isCash(): Boolean {
        return id == "CASH"
    }
}
