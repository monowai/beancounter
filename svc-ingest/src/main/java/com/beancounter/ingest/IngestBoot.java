package com.beancounter.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * Read a google sheet and create an output file.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@SpringBootApplication
public class IngestBoot {

  public static void main(String[] args) {
    SpringApplication.run(IngestBoot.class, args);
  }
}
