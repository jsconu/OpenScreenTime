package org.openscreentime.shared.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpBotTest {

    private val knowledge = HelpBot.loadKnowledge()
    private val bot = HelpBot(knowledge)

    // --- The knowledge base itself ---

    @Test
    fun `the packaged knowledge base loads`() {
        assertTrue("knowledge.json should be on the classpath", knowledge.entries.isNotEmpty())
    }

    @Test
    fun `the iOS copy of the knowledge base is byte-identical`() {
        // Unit tests run with the module directory as the working directory.
        val shared = java.io.File("src/main/resources/helpbot/knowledge.json")
        val ios = java.io.File("../ios/Sources/OpenScreenTimeParentKit/Resources/knowledge.json")
        assertTrue("shared knowledge.json missing", shared.exists())
        assertTrue("iOS knowledge.json missing - copy the shared file over", ios.exists())
        assertArrayEquals("edit shared/.../knowledge.json, then copy it to the iOS Resources", shared.readBytes(), ios.readBytes())
    }

    @Test
    fun `entry ids are unique`() {
        val ids = knowledge.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every audience value is one the bot understands`() {
        assertTrue(knowledge.entries.all { it.audience in setOf("parent", "kid", "both") })
    }

    @Test
    fun `age-routed guidance entries all exist`() {
        val ids = knowledge.entries.map { it.id }.toSet()
        listOf(0, 3, 8, 30).forEach { assertTrue(HelpBot.guidanceEntryIdForAge(it) in ids) }
    }

    @Test
    fun `every guidance entry that states a figure cites a source`() {
        val citedIds = listOf(
            "guidance-overview", "guidance-under-2", "guidance-2-4", "guidance-5-17",
            "guidance-aap", "guidance-uk-cmo", "guidance-sleep", "guidance-quality"
        )
        for (id in citedIds) {
            val entry = knowledge.entries.first { it.id == id }
            assertTrue("$id should cite at least one source", entry.sources.isNotEmpty())
        }
    }

    @Test
    fun `every source has a title and an https url`() {
        for (source in knowledge.entries.flatMap { it.sources }) {
            assertTrue(source.title.isNotBlank())
            assertTrue(source.url.startsWith("https://"))
        }
    }

    @Test
    fun `health guidance always says it is not medical advice`() {
        for (id in listOf("guidance-overview", "guidance-under-2", "guidance-2-4", "guidance-5-17", "guidance-sleep")) {
            val answer = knowledge.entries.first { it.id == id }.answer
            assertTrue("$id should carry the disclaimer", answer.contains("not medical advice"))
        }
    }

    // --- Every entry is reachable through its own question ---

    @Test
    fun `asking an entry's own question returns that entry`() {
        for (audience in HelpAudience.entries) {
            for (entry in knowledge.entries.filter { it.audience == "both" || it.audience == audience.wireValue }) {
                val reply = bot.reply(entry.question, audience)
                assertEquals(
                    "'${entry.question}' (${audience.wireValue}) should reach ${entry.id}",
                    entry.id, reply.matchedEntryId
                )
            }
        }
    }

    @Test
    fun `every suggested question gets a real answer`() {
        for (audience in HelpAudience.entries) {
            for (question in bot.suggestions(audience)) {
                assertNotNull("'$question' should match an entry", bot.reply(question, audience).matchedEntryId)
            }
        }
    }

    // --- Age-aware limits ---

    private fun matched(query: String, audience: HelpAudience = HelpAudience.PARENT) =
        bot.reply(query, audience).matchedEntryId

    @Test
    fun `a toddler's age routes to the under-2 guidance`() {
        assertEquals("guidance-under-2", matched("How much screen time should my 1 year old have?"))
        assertEquals("guidance-under-2", matched("screen time limit for my 10 month old"))
    }

    @Test
    fun `a preschooler's age routes to the 2 to 4 guidance`() {
        assertEquals("guidance-2-4", matched("How much screen time is ok for my 4 year old?"))
    }

    @Test
    fun `a school-age child's age routes to the 5 to 17 guidance`() {
        assertEquals("guidance-5-17", matched("What screen time limit should I set for my 7 year old"))
        assertEquals("guidance-5-17", matched("how many hours a day for a 14-year-old"))
    }

    @Test
    fun `asking about yourself routes to the adult guidance`() {
        assertEquals("guidance-adults", matched("How much screen time should I set for myself?"))
    }

    @Test
    fun `forgetting the account password reaches the reset entry, not the family passcode one`() {
        assertEquals("password-reset", matched("I forgot my password"))
        assertEquals("password-reset", matched("how do I reset my password"))
    }

    @Test
    fun `age extraction handles the common phrasings`() {
        assertEquals(4, HelpBot.extractAgeYears("my 4 year old"))
        assertEquals(9, HelpBot.extractAgeYears("a 9-year-old"))
        assertEquals(12, HelpBot.extractAgeYears("aged 12"))
        assertEquals(7, HelpBot.extractAgeYears("my 7yo"))
        assertEquals(0, HelpBot.extractAgeYears("my 8 month old"))
        assertNull(HelpBot.extractAgeYears("how do I set a bedtime"))
    }

    @Test
    fun `age extraction handles compound ages and ignores look-alikes`() {
        assertEquals(1, HelpBot.extractAgeYears("my 1 year 6 months old"))
        assertEquals(2, HelpBot.extractAgeYears("a 2 years and 3 months old"))
        assertEquals(2, HelpBot.extractAgeYears("my 24 month old"))
        assertNull(HelpBot.extractAgeYears("in 2024 year old phones are common"))
        assertNull(HelpBot.extractAgeYears("usage 10 hours"))
    }

    @Test
    fun `plurals stem to the same token as their singular`() {
        assertEquals(HelpBot.tokenize("time"), HelpBot.tokenize("times"))
        assertEquals(HelpBot.tokenize("note"), HelpBot.tokenize("notes"))
        assertEquals(HelpBot.tokenize("watch"), HelpBot.tokenize("watches"))
    }

    // --- How-to and philosophy ---

    @Test
    fun `common how-to questions reach the right entry`() {
        assertEquals("bedtime", matched("how do I set a bedtime?"))
        assertEquals("pairing", matched("how do I pair my child's phone"))
        assertEquals("weekly-report", matched("what is the weekly report"))
        assertEquals("lock-now", matched("I need to lock the phone right now"))
    }

    @Test
    fun `the philosophy questions reach their entries`() {
        assertEquals("why-no-numbers-kid", matched("why can't my child see their exact numbers"))
        assertEquals("philosophy-overview", matched("what is your design philosophy"))
    }

    @Test
    fun `an answer offers up to two related questions, never the same one`() {
        val reply = bot.reply("How much screen time is reasonable?", HelpAudience.PARENT)
        assertTrue(reply.relatedQuestions.size <= 2)
        val best = knowledge.entries.first { it.id == reply.matchedEntryId }
        assertFalse(best.question in reply.relatedQuestions)
    }

    // --- Audience ---

    @Test
    fun `a kid never gets parent-only setup answers`() {
        assertFalse(matched("how do I pair my child's phone", HelpAudience.KID) == "pairing")
    }

    @Test
    fun `a kid can still ask for more time`() {
        assertEquals("request-more-time", matched("how do I ask for more time", HelpAudience.KID))
    }

    // --- Fallback ---

    @Test
    fun `an unrelated question falls back honestly with suggestions`() {
        val reply = bot.reply("what is the airspeed velocity of an unladen swallow", HelpAudience.PARENT)
        assertNull(reply.matchedEntryId)
        assertTrue(reply.text.startsWith("I don't have an answer"))
        assertEquals(bot.suggestions(HelpAudience.PARENT), reply.relatedQuestions)
    }

    @Test
    fun `an empty question falls back too`() {
        assertNull(bot.reply("   ", HelpAudience.PARENT).matchedEntryId)
    }

    @Test
    fun `tokenizing drops stopwords and lightly stems`() {
        assertEquals(setOf("limit", "app"), HelpBot.tokenize("What are the limits for apps?"))
    }
}
