package org.openscreentime.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A cited public source behind a piece of screen-time guidance. */
@Serializable
data class HelpSource(val title: String, val url: String)

/**
 * One question the help bot can answer. [audience] is "parent", "kid", or "both". Answers to
 * health-related questions carry [sources] - they're written from public guidance (WHO, the
 * Canadian 24-Hour Movement Guidelines, the AAP, the UK Chief Medical Officers), not generated.
 */
@Serializable
data class HelpEntry(
    val id: String,
    val audience: String = "both",
    val topic: String,
    val question: String,
    val keywords: List<String>,
    val answer: String,
    val sources: List<HelpSource> = emptyList()
)

@Serializable
data class HelpKnowledge(
    val version: Int,
    val entries: List<HelpEntry>,
    val suggestions: Map<String, List<String>> = emptyMap()
)

enum class HelpAudience(val wireValue: String) { PARENT("parent"), KID("kid") }

/** What the bot says back: [text], any [sources], and other questions it can answer nearby. */
data class HelpReply(
    val text: String,
    val sources: List<HelpSource> = emptyList(),
    val relatedQuestions: List<String> = emptyList(),
    val matchedEntryId: String? = null
)

/**
 * See #36 - the in-app help bot. Deliberately not a language model: a small, offline, private,
 * auditable retriever over a curated, cited knowledge base (`helpbot/knowledge.json`). A family
 * app that's free, serverless, and asks nothing of a child shouldn't ship every question to a
 * third party or risk an invented answer to a health question. It only ever says what's in the
 * knowledge base, and says so plainly when it has no answer.
 */
class HelpBot(private val knowledge: HelpKnowledge) {

    fun suggestions(audience: HelpAudience): List<String> =
        knowledge.suggestions[audience.wireValue].orEmpty()

    fun reply(query: String, audience: HelpAudience): HelpReply {
        val visible = knowledge.entries.filter { it.audience == "both" || it.audience == audience.wireValue }
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return fallback(audience)

        val age = extractAgeYears(query)
        val aboutLimits = tokens.any { it in LIMIT_WORDS }
        val ageEntryId = if (age != null && aboutLimits) guidanceEntryIdForAge(age) else null
        val normalizedQuery = " " + tokens.joinToString(" ") + " "

        val scored = visible
            .map { entry -> entry to score(entry, tokens, normalizedQuery) + if (entry.id == ageEntryId) AGE_BOOST else 0 }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }

        val best = scored.firstOrNull() ?: return fallback(audience)
        val related = scored.drop(1).take(MAX_RELATED).map { it.first.question }
        return HelpReply(
            text = best.first.answer,
            sources = best.first.sources,
            relatedQuestions = related,
            matchedEntryId = best.first.id
        )
    }

    private fun fallback(audience: HelpAudience): HelpReply = HelpReply(
        text = "I don't have an answer for that yet. I can help with how to use OpenScreenTime, " +
            "why it's designed the way it is, and what published guidance says about reasonable " +
            "screen time limits. Try one of these:",
        relatedQuestions = suggestions(audience)
    )

    private fun score(entry: HelpEntry, queryTokens: Set<String>, normalizedQuery: String): Int {
        var total = 0
        val keywordTokens = entry.keywords.flatMap { tokenize(it) }.toSet()
        val questionTokens = tokenize(entry.question)
        val answerTokens = tokenize(entry.answer)
        for (t in queryTokens) {
            if (t in keywordTokens) total += KEYWORD_WEIGHT
            if (t in questionTokens) total += QUESTION_WEIGHT
            if (t in answerTokens) total += ANSWER_WEIGHT
        }
        for (phrase in entry.keywords) {
            val stemmed = tokenize(phrase)
            if (stemmed.size >= 2 && normalizedQuery.contains(" " + stemmed.joinToString(" ") + " ")) {
                total += PHRASE_WEIGHT
            }
        }
        return total
    }

    companion object {
        private const val MIN_SCORE = 5
        private const val MAX_RELATED = 2
        private const val KEYWORD_WEIGHT = 4
        private const val QUESTION_WEIGHT = 2
        private const val ANSWER_WEIGHT = 1
        private const val PHRASE_WEIGHT = 6
        private const val AGE_BOOST = 100

        private val LIMIT_WORDS = setOf(
            "limit", "much", "hour", "minute", "allow", "reasonable", "recommend", "should",
            "set", "healthy", "normal", "enough", "long", "time", "screen"
        )

        private val STOPWORDS = setOf(
            "a", "an", "the", "is", "are", "was", "were", "do", "does", "did", "how", "what", "i", "my",
            "me", "to", "of", "for", "can", "we", "it", "in", "on", "and", "or", "when", "why", "with",
            "this", "that", "be", "you", "your", "there", "if", "at", "about", "so", "as", "am", "have",
            "has", "any", "some", "get", "them", "they", "their", "would", "could", "please", "tell"
        )

        /** Lowercased, punctuation-stripped, stopword-free, lightly stemmed tokens. */
        fun tokenize(text: String): Set<String> =
            text.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.isNotEmpty() && it !in STOPWORDS }
                .map(::stem)
                .toSet()

        private fun stem(word: String): String = when {
            word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
            word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
            word.length > 4 && word.endsWith("es") -> word.dropLast(2)
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
            else -> word
        }

        private val AGE_PATTERNS = listOf(
            Regex("""(\d{1,2})\s*-?\s*(?:year|yr)s?\s*-?\s*old"""),
            Regex("""(?:age|aged)\s*(\d{1,2})"""),
            Regex("""(\d{1,2})\s*(?:yo|y/o)\b"""),
            Regex("""(\d{1,2})\s*(?:month)s?\s*-?\s*old""")
        )

        /** "my 4 year old", "aged 7", "a 9yo" -> the age in years; months give 0 (an infant). */
        fun extractAgeYears(query: String): Int? {
            val q = query.lowercase()
            if (AGE_PATTERNS[3].containsMatchIn(q)) return 0
            for (pattern in AGE_PATTERNS.take(3)) {
                val n = pattern.find(q)?.groupValues?.get(1)?.toIntOrNull()
                if (n != null && n in 0..99) return n
            }
            return null
        }

        /** Which guidance entry answers "how much screen time" for a child this age. */
        fun guidanceEntryIdForAge(ageYears: Int): String = when {
            ageYears < 2 -> "guidance-under-2"
            ageYears < 5 -> "guidance-2-4"
            ageYears < 18 -> "guidance-5-17"
            else -> "guidance-adults"
        }

        private val json = Json { ignoreUnknownKeys = true }

        fun parseKnowledge(raw: String): HelpKnowledge = json.decodeFromString(raw)

        /** Loads the packaged knowledge base; empty (so the bot only ever falls back) if it can't be read. */
        fun loadKnowledge(): HelpKnowledge =
            try {
                val stream = HelpBot::class.java.classLoader?.getResourceAsStream(KNOWLEDGE_RESOURCE)
                    ?: return HelpKnowledge(version = 0, entries = emptyList())
                parseKnowledge(stream.bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                HelpKnowledge(version = 0, entries = emptyList())
            }

        const val KNOWLEDGE_RESOURCE = "helpbot/knowledge.json"

        /** The one bot both apps use. */
        val default: HelpBot by lazy { HelpBot(loadKnowledge()) }
    }
}
