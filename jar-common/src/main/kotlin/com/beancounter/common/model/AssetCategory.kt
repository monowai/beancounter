package com.beancounter.common.model

import org.springframework.boot.context.properties.ConstructorBinding

data class AssetCategory @ConstructorBinding constructor(var id: String, var name: String)
