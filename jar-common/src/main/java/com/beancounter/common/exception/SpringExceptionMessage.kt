package com.beancounter.common.exception

import java.util.Date

/**
 * Concrete view of the exception structure we return between services.
 * Base on the Spring exception structure
 * @author mikeh
 * @since 2019-02-04
 */
class SpringExceptionMessage {
    var timestamp = Date()
    var status = 0
    var error: String? = null
    var message: String? = null
    var path: String? = null

    constructor(timestamp: Date = Date(), status: Int, error: String?, message: String?, path: String?) {
        this.timestamp = timestamp
        this.status = status
        this.error = error
        this.message = message
        this.path = path
    }

    constructor() {
        timestamp = Date()
    }
}
