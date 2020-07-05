package com.beancounter.common.contracts

import com.beancounter.common.model.Asset
import org.springframework.boot.context.properties.ConstructorBinding

data class AssetResponse @ConstructorBinding constructor(override val data: Asset) : Payload<Asset?>