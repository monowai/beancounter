package com.beancounter.shell.config

import com.beancounter.client.config.ClientConfig
import com.beancounter.common.utils.UtilConfig
import com.beancounter.shell.cli.DataCommands
import com.beancounter.shell.cli.EnvCommands
import com.beancounter.shell.cli.PortfolioCommands
import com.beancounter.shell.cli.ShellPrompt
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
/**
 * Helper class to import necessary shell based dependencies.
 */
@Import(
    EnvConfig::class,
    UtilConfig::class,
    EnvCommands::class,
    ShellPrompt::class,
    PortfolioCommands::class,
    DataCommands::class,
    ClientConfig::class,
)
class ShellConfig
