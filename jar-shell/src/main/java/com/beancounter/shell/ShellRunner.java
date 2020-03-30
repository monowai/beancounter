package com.beancounter.shell;

import com.beancounter.auth.client.AuthClientConfig;
import com.beancounter.client.config.ClientConfig;
import com.beancounter.client.sharesight.ShareSightConfig;
import com.beancounter.common.utils.UtilConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


/**
 * Read a google sheet and create an output file.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@SpringBootApplication(scanBasePackageClasses = {
    AuthClientConfig.class,
    ShareSightConfig.class,
    UtilConfig.class,
    ClientConfig.class},
    scanBasePackages = {
        "com.beancounter.shell"})
@EnableConfigurationProperties
public class ShellRunner {
  public static void main(String[] args) {
    SpringApplication.run(ShellRunner.class, args);
  }
}
