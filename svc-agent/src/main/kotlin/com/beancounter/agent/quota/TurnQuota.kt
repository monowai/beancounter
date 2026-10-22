package com.beancounter.agent.quota

import com.beancounter.agent.conversation.TurnRecorder

/** Admits and counts the AI turns of a metered caller. */
interface TurnQuota {
    /** True while [ownerId] has a turn left in the window. */
    fun allows(ownerId: String): Boolean

    /** Count one answered turn against [ownerId]. */
    fun spend(ownerId: String)

    /** True when an administrator has turned AI off for [ownerId] (a limit of 0). */
    fun disabled(ownerId: String): Boolean

    companion object {
        /** No quota: every turn is admitted and nothing is counted. */
        val NONE =
            object : TurnQuota {
                override fun allows(ownerId: String) = true

                override fun spend(ownerId: String) = Unit

                override fun disabled(ownerId: String) = false
            }
    }
}

/**
 * Records a turn as [delegate] would and, once it is answered, counts it
 * against [ownerId]. A failed turn is never counted. The turn is counted even
 * when recording it fails: the model has answered.
 */
class QuotaRecorder(
    private val delegate: TurnRecorder,
    private val quota: TurnQuota,
    private val ownerId: String
) : TurnRecorder {
    override fun answered(content: String) {
        try {
            delegate.answered(content)
        } finally {
            quota.spend(ownerId)
        }
    }

    override fun failed(code: String) = delegate.failed(code)
}