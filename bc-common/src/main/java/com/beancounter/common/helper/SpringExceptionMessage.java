package com.beancounter.common.helper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author mikeh
 * @since 2019-02-04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SpringExceptionMessage {

    private String timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

}
