package com.beancounter.agent.screenshot

/** An uploaded image, held in memory for one read and never stored. */
class Screenshot(
    val bytes: ByteArray,
    val mediaType: String
)

/** A model that answers an instruction about an image with text. */
fun interface VisionModel {
    /** @throws VisionUnavailableException when the model cannot be reached or answers nothing */
    fun read(
        instruction: String,
        screenshot: Screenshot
    ): String
}

/**
 * The vision model failed or gave no answer; the screenshot itself may be fine.
 *
 * @property refused the gateway turned the request away (its key or model is not allowed),
 * which retrying will not fix; false for an outage or an empty answer
 */
class VisionUnavailableException(
    message: String,
    cause: Throwable? = null,
    val refused: Boolean = false
) : RuntimeException(message, cause)

/** The model answered, but not with a proposal: the image is not a readable brokerage page. */
class UnreadableScreenshotException(
    cause: Throwable? = null
) : RuntimeException("The screenshot could not be read as holdings or transactions", cause)