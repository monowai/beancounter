package com.beancounter.common.input

import com.beancounter.common.model.Portfolio

/**
 * Minimal arguments to import Trn related data.
 */
interface TrnImport {
    val portfolio: Portfolio
    val importFormat: ImportFormat
    val message: String
}

/**
 * Delimited formats supported by BC.
 */
enum class ImportFormat {
    BC,
    SHARESIGHT
}