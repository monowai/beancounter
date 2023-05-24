package com.beancounter.common.model

import org.springframework.boot.context.properties.ConstructorBinding

/**
 * Simple classification structure of an Asset.
 */
data class AssetCategory @ConstructorBinding constructor(var id: String, var name: String) {
    companion object {
        const val RE: String = "RE"
    }
}
