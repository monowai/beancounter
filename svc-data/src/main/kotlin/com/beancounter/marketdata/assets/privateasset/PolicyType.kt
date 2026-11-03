package com.beancounter.marketdata.assets.privateasset

/**
 * Type of composite policy asset.
 * Used to determine projection behaviour and UI templates.
 */
enum class PolicyType {
    CPF,
    GENERIC,
    US_401K,
    US_IRA,
    UK_ISA
}