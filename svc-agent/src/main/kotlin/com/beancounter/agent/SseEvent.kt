package com.beancounter.agent

/** Event names on the agent's answer stream; bc-view and bc-mobile switch on them. */
object SseEvent {
    const val TOKEN = "token"
    const val DONE = "done"
    const val ERROR = "error"
    const val RESET = "reset"
}