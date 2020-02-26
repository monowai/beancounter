package com.beancounter.shell.cli;

import com.beancounter.shell.auth.LoginService;
import com.beancounter.shell.config.GoogleAuthConfig;
import java.nio.file.FileSystems;
import org.jline.reader.LineReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class UtilCommands {
  private GoogleAuthConfig googleAuthConfig;
  private LoginService loginService;
  private LineReader lineReader;

  UtilCommands(GoogleAuthConfig googleAuthConfig, LoginService loginService) {
    this.googleAuthConfig = googleAuthConfig;
    this.loginService = loginService;
  }

  @Autowired
  public void setLineReader(@Lazy LineReader lineReader) {
    this.lineReader = lineReader;
  }

  @ShellMethod("working dir")
  public String pwd() {
    return FileSystems.getDefault().getPath(".").toAbsolutePath().toString();
  }

  @ShellMethod("Secrets")
  public String api() {
    return googleAuthConfig.getApiPath();
  }

  @ShellMethod("Identify yourself")
  public void login(
      @ShellOption(help = "User ID") String user) {

    String password = lineReader.readLine("Password: ", '*');
    this.loginService.login(user, password);

  }

}
