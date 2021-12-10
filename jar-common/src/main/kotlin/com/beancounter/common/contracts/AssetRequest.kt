package com.beancounter.common.contracts

import com.beancounter.common.input.AssetInput
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * Contract to create a collection of assets.
 */
data class AssetRequest @ConstructorBinding
constructor(override var data: Map<String, AssetInput> = HashMap()) : Payload<Map<String, AssetInput>> {
    constructor(assetInput: AssetInput, code: String = assetInput.code) : this(data = mapOf(code to assetInput))
}
