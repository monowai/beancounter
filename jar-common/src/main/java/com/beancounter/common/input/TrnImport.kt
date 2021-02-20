package com.beancounter.common.input

import com.beancounter.common.model.Portfolio

interface TrnImport {
    val portfolio: Portfolio
    val message: String?
}
