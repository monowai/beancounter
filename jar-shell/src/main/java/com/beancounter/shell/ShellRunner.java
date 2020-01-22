package com.beancounter.shell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * Read a google sheet and create an output file.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@SpringBootApplication
public class ShellRunner {
  public static void main(String[] args) {
    SpringApplication.run(ShellRunner.class, args);
  }
}
