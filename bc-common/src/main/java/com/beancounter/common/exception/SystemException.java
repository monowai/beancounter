package com.beancounter.common.exception;

import lombok.Data;

/**
 * Classification for integration or other system failures
 *
 * @author mikeh
 * @since 2019-02-03
 */
@Data
public class SystemException extends RuntimeException {
    private int status;
    public SystemException(String message) {
        super(message);
    }

    /**
     * Typically HTTPStatus 500-599. Unexpected failures
     *
     * @author mikeh
     * @since 2019-02-03
     */
    public SystemException(int status, String reason) {
        this(reason);
        this.status = status;
    }

}