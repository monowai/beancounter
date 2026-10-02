package com.beancounter.agent.conversation

/**
 * The provisional title a conversation gets the moment its first question
 * lands: the question's key words, with question words, pronouns, stopwords
 * and conversational filler removed. [ConversationTitler] later replaces it
 * with a model-written title.
 */
object ConversationTitles {
    const val PROVISIONAL_MAX_CHARS = 40
    private const val FALLBACK_MAX_CHARS = 60

    private val FILLER =
        Regex(
            "\\b(in (a|an|one|two|three|four|five|a few|\\d+) (short |brief )?(bullet )?" +
                "(sentences?|lines?|words?|paragraphs?|points?|bullets?|steps?)|" +
                "in short|briefly|please|thanks|thank you|" +
                "can you|could you|would you|will you|tell me|show me|let me know|" +
                "i want to know|i'd like to know|i would like to know)\\b",
            RegexOption.IGNORE_CASE
        )
    private val WORD = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}&'.-]*")
    private val WHITESPACE = Regex("\\s+")
    private val STOPWORDS =
        setOf(
            "a",
            "an",
            "the",
            "of",
            "to",
            "in",
            "on",
            "at",
            "for",
            "from",
            "by",
            "with",
            "about",
            "into",
            "over",
            "and",
            "or",
            "but",
            "vs",
            "versus",
            "is",
            "are",
            "was",
            "were",
            "be",
            "been",
            "being",
            "am",
            "do",
            "does",
            "doing",
            "have",
            "has",
            "had",
            "should",
            "would",
            "could",
            "can",
            "will",
            "shall",
            "may",
            "might",
            "must",
            "what",
            "what's",
            "whats",
            "how",
            "why",
            "when",
            "where",
            "who",
            "whom",
            "which",
            "whose",
            "that",
            "this",
            "these",
            "those",
            "there",
            "here",
            "it",
            "it's",
            "its",
            "i",
            "i'm",
            "me",
            "my",
            "mine",
            "we",
            "us",
            "our",
            "ours",
            "you",
            "your",
            "yours",
            "they",
            "them",
            "their",
            "he",
            "she",
            "his",
            "her",
            "if",
            "then",
            "than",
            "so",
            "as",
            "just",
            "also",
            "any",
            "some",
            "all",
            "very",
            "really",
            "much",
            "many",
            "more",
            "most",
            "up",
            "out",
            "again",
            "only",
            "too",
            "not",
            "no",
            "yes",
            "ok",
            "okay",
            "hi",
            "hello",
            "hey",
            "explain",
            "know",
            "want",
            "need",
            "like",
            "let",
            "lets",
            "let's",
            "give",
            "get",
            "got"
        )

    fun provisional(question: String): String {
        val words =
            WORD
                .findAll(FILLER.replace(question, " "))
                .map { it.value.trimEnd('.', '\'', '-') }
                .filter { it.isNotEmpty() && it.lowercase() !in STOPWORDS }
                .toList()
        if (words.isEmpty()) return fallback(question)
        val title = StringBuilder()
        for (word in words) {
            val next = if (title.isEmpty()) word else "$title $word"
            if (next.length > PROVISIONAL_MAX_CHARS) break
            title.clear().append(next)
        }
        val text = title.ifEmpty { words.first().take(PROVISIONAL_MAX_CHARS) }.toString()
        return text.replaceFirstChar { it.uppercaseChar() }
    }

    private fun fallback(question: String): String {
        val flat = question.trim().replace(WHITESPACE, " ")
        return if (flat.length <= FALLBACK_MAX_CHARS) flat else flat.take(FALLBACK_MAX_CHARS - 1).trimEnd() + "…"
    }
}