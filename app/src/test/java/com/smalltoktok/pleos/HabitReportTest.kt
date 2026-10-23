package com.smalltoktok.pleos

import com.smalltoktok.pleos.data.HabitAnswer
import com.smalltoktok.pleos.data.HabitRecord
import com.smalltoktok.pleos.ui.buildReport
import com.smalltoktok.pleos.ui.spoken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HabitReportTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z")

    private fun record(daysAgo: Long, distance: String, vararg answers: HabitAnswer, phrases: List<String> = emptyList()) =
        HabitRecord(now.minus(Duration.ofDays(daysAgo)), distance, answers.toList(), phrases, answers.size)

    private fun a(shape: String, context: String, delay: Long?, vararg signals: String) =
        HabitAnswer(shape, signals.toList(), context, delay)

    @Test
    fun 대화가_세_번이_안_되면_리포트가_없다() {
        val records = listOf(record(1, "daily", a("reaction_plus", "asked", 1000)), record(2, "daily", a("safe_reaction", "asked", 1000)))
        assertNull(buildReport(records, now))
    }

    @Test
    fun 최근_7일을_세고_지난주와_비교한다() {
        val records = listOf(
            record(1, "first-meet", a("safe_reaction", "personal", 5000, "filler"), a("reaction_plus", "asked", 1200), phrases = listOf("아 진짜요?")),
            record(2, "first-meet", a("short_answer", "praised", 3000), a("safe_reaction", "personal", 4000), phrases = listOf("아 진짜요?", "네네")),
            record(3, "daily", a("reaction_plus", "shared", 1000, "empathy"), a("short_answer", "praised", 2000), a("short_answer", "praised", 2500)),
            // 지난주
            record(9, "daily", a("safe_reaction", "asked", 1500)),
        )
        val r = buildReport(records, now)
        assertNotNull(r)
        r!!
        assertEquals(3, r.conversations)
        assertEquals(7, r.answers)
        assertEquals(28, r.reactionPlusRate)
        assertEquals(2, r.reactionPlusDelta)
        assertEquals("아 진짜요?" to 2, r.phrases.first())
        assertEquals("personal", r.stuck?.context)
        assertEquals("praised", r.situation?.context)
        assertEquals("short_answer", r.situation?.shape)
        assertEquals("first-meet", r.weakestDistance)
        assertTrue(r.tip.title.contains("칭찬"))
        assertTrue(r.spoken("형민").startsWith("형민님"))
    }
}
