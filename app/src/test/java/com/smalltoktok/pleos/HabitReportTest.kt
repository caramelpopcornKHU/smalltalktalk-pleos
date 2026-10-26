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
    private val now = Instant.parse("2026-10-26T12:00:00Z")

    private fun record(daysAgo: Long, distance: String, vararg answers: HabitAnswer, phrases: List<String> = emptyList(), difficulty: String? = "normal") =
        HabitRecord(now.minus(Duration.ofDays(daysAgo)), distance, difficulty, answers.toList(), phrases, answers.size)

    /** marks: reaction, jump, question, detail, invite 순서로 h(hit) / m(miss) / n(na) */
    private fun a(marks: String, context: String = "asked", delay: Long? = 1000, shape: String = "reaction_plus", vararg slips: String): HabitAnswer {
        val keys = listOf("reaction", "jump", "question", "detail", "invite")
        val map = keys.zip(marks.toList()).associate { (k, c) -> k to when (c) { 'h' -> "hit"; 'm' -> "miss"; else -> "na" } }
        return HabitAnswer(shape, map, slips.toList(), context, delay)
    }

    @Test
    fun 대화가_세_번이_안_되면_리포트가_없다() {
        val records = listOf(record(1, "daily", a("hhnhn")), record(2, "daily", a("hhnhn")))
        assertNull(buildReport(records, now))
    }

    @Test
    fun 기준별로_해당하는_답만_세고_가장_약한_기준을_고른다() {
        val records = listOf(
            record(1, "first-meet", a("hmmmh", "personal", 5000), a("hhmmn", "asked", 1200, "question_back", "interview"), phrases = listOf("아 진짜요?")),
            record(2, "first-meet", a("mmnmm", "praised", 3000, "short_answer"), a("hmmmn", "personal", 4000), phrases = listOf("아 진짜요?", "네네")),
            record(3, "daily", a("hhhhn", "shared", 1000), a("hhnhh", "asked", 2000), difficulty = "hard"),
            // 지난주
            record(9, "daily", a("mmmmn", "asked", 1500)),
        )
        val r = buildReport(records, now)
        assertNotNull(r)
        r!!
        assertEquals(3, r.conversations)
        assertEquals(6, r.answers)
        val byKey = r.criteria.associateBy { it.key }
        assertEquals(5, byKey.getValue("reaction").hit)
        assertEquals(6, byKey.getValue("reaction").total)
        // 질문이 있던 답(na가 아닌 답)만 센다: hit 1 / 전체 4
        assertEquals(1, byKey.getValue("question").hit)
        assertEquals(4, byKey.getValue("question").total)
        assertEquals("question", r.weakest?.key)
        assertEquals("reaction", r.best?.key)
        assertEquals("interview" to 1, r.slips.first())
        assertEquals(1, r.hardConversations)
        assertEquals("아 진짜요?" to 2, r.phrases.first())
        assertEquals("personal", r.stuck?.context)
        assertEquals("first-meet", r.weakestDistance)
        assertTrue(r.spoken("형민").startsWith("형민님"))
        assertTrue(r.spoken(null).contains("여는 질문"))
    }
}
