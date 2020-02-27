package com.beancounter.shell.cli;

import java.nio.file.FileSystems;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class UtilCommands {
  @Value("${api.path:../secrets/google-api/}")
  private String apiPath;

  @ShellMethod("working dir")
  public String pwd() {
    return FileSystems.getDefault().getPath(".").toAbsolutePath().toString();
  }

  @ShellMethod("Secrets")
  public String api() {
    return apiPath;
  }


}
