package com.beancounter.common.contracts

/**
 * Simple Payload interface to type a response.
 */
interface Payload<T> {
    val data: T

    companion object {
        const val DATA = "data"
    }
}