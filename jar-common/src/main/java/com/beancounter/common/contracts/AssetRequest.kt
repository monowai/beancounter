package com.beancounter.common.contracts

import com.beancounter.common.input.AssetInput
import org.springframework.boot.context.properties.ConstructorBinding
import java.util.HashMap

data class AssetRequest @ConstructorBinding
constructor(override var data: Map<String, AssetInput> = HashMap()) : Payload<Map<String, AssetInput>> {
    constructor(code: String, assetInput: AssetInput) : this(data = hashMapOf(code to assetInput))
}
