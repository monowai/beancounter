package com.beancounter.common.contracts

import com.beancounter.common.input.AssetInput

/**
 * Contract to create a collection of assets.
 */
data class AssetRequest(override var data: Map<String, AssetInput> = mapOf()) : Payload<Map<String, AssetInput>> {
    constructor(assetInput: AssetInput, code: String = assetInput.code) : this(data = mapOf(code to assetInput))
}
