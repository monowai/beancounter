package com.beancounter.shell.cli;

import com.beancounter.shell.config.GoogleAuthConfig;
import java.nio.file.FileSystems;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class UtilCommands {
  private GoogleAuthConfig googleAuthConfig;

  @Autowired
  void setGoogleAuth(GoogleAuthConfig googleAuthConfig) {
    this.googleAuthConfig = googleAuthConfig;
  }

  @ShellMethod("working dir")
  public String pwd() {
    return FileSystems.getDefault().getPath(".").toAbsolutePath().toString();
  }

  @ShellMethod("Secrets")
  public String api() {
    return googleAuthConfig.getApiPath();
  }

}
