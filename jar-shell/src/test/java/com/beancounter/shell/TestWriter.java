package com.beancounter.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.SystemException;
import com.beancounter.shell.writer.FileIngestWriter;
import com.beancounter.shell.writer.IngestWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class TestWriter {

  @Test
  void is_NullWriter() {
    assertThat(new FileIngestWriter().prepareFile(null)).isNull();
  }

  @Test
  void is_FailingFile() {
    assertThrows(SystemException.class, () -> new FileIngestWriter()
        .prepareFile(""));
  }

  @Test
  void is_IngestWriter() throws IOException {
    IngestWriter ingestWriter = new FileIngestWriter();
    String path = System.getProperty("java.io.tmpdir");
    if (!path.endsWith("/")) {
      path = path + "/";
    }
    String tempFile = path + System.currentTimeMillis() + ".tmp";
    log.info("tempFile {}", tempFile);
    OutputStream file = ingestWriter.prepareFile(tempFile);
    file.write("TestFile".getBytes());
    file.close();
    File fileToDelete = new File(tempFile);
    if (fileToDelete.exists()) {
      assertThat(fileToDelete.delete()).isTrue();
    }

  }
}
