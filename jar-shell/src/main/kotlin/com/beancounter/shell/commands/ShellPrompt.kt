package com.beancounter.shell.commands

import org.jline.utils.AttributedString
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.shell.jline.PromptProvider
import org.springframework.stereotype.Component

/**
 * Customise the prompt in the Shell.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ShellPrompt : PromptProvider {
    override fun getPrompt(): AttributedString {
        val builder = AttributedStringBuilder()
        builder.append(
            "bc-shell$ ",
            AttributedStyle.DEFAULT,
        )
        return builder.toAttributedString()
    }
}
