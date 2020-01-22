package com.beancounter.shell.writer;

import com.beancounter.common.exception.SystemException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FileIngestWriter implements IngestWriter {
  public FileOutputStream prepareFile(String file) {
    if (file == null) {
      return null;
    }
    try {
      return new FileOutputStream(file);
    } catch (FileNotFoundException e) {
      throw new SystemException(String.format("File not found %s", file));
    }
  }

}
