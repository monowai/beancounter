package com.beancounter.common.input

import com.beancounter.common.model.Portfolio

interface TrnImport {
    val portfolio: Portfolio
    val importFormat: ImportFormat
    val message: String?
}

enum class ImportFormat {
    BC, SHARESIGHT
}
