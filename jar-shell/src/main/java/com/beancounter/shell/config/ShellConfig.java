package com.beancounter.shell.config;

import com.beancounter.client.ClientConfig;
import com.beancounter.common.utils.UtilConfig;
import com.beancounter.shell.cli.DataCommands;
import com.beancounter.shell.cli.ShellPrompt;
import com.beancounter.shell.cli.UserCommands;
import com.beancounter.shell.cli.UtilCommands;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    UtilConfig.class,
    UtilCommands.class,
    UserCommands.class,
    ShellPrompt.class,
    DataCommands.class,
    ClientConfig.class})
public class ShellConfig {
}
