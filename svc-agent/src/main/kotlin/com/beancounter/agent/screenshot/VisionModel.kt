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

/** The vision model failed or gave no answer; the screenshot itself may be fine. */
class VisionUnavailableException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/** The model answered, but not with a proposal: the image is not a readable brokerage page. */
class UnreadableScreenshotException(
    cause: Throwable? = null
) : RuntimeException("The screenshot could not be read as holdings or transactions", cause)