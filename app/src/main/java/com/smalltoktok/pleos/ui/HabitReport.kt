package com.smalltoktok.pleos.ui

import com.smalltoktok.pleos.data.HabitRecord
import java.time.Duration
import java.time.Instant

// 나의 대화 습관 리포트 계산 (2026-10-23, 시안 smalltalktalk docs/prototypes/habit-report.html).
// 서버가 대화마다 저장한 요약 태그를 이 기기에서 모아 센다. 모델을 부르지 않는다.
// 태그 이름은 smalltalktalk packages/shared/src/habits.ts와 같다.

val SHAPE_LABELS = linkedMapOf(
    "reaction_plus" to "리액션+한 마디",
    "safe_reaction" to "맞장구만",
    "question_back" to "되묻기",
    "self_share" to "내 이야기",
    "short_answer" to "단답",
    "fact_only" to "정보만",
    "topic_shift" to "화제 바꾸기",
    "no_answer" to "말하지 못함",
)

val SIGNAL_LABELS = mapOf(
    "filler" to "시간 끌기 말",
    "self_lowering" to "자기 낮추기",
    "over_apology" to "사과가 많음",
    "closer" to "대화 닫는 말",
    "echo_only" to "따라 하기만",
    "over_explain" to "설명이 너무 김",
    "interview_mode" to "질문 폭격",
    "negative_start" to "부정으로 시작",
    "speech_slip" to "말투 흔들림",
    "empathy" to "공감 표현",
    "compliment" to "칭찬",
    "callback" to "상대 말 기억해 꺼내기",
)
val GOOD_SIGNALS = setOf("empathy", "compliment", "callback")

val CONTEXT_LABELS = mapOf(
    "opening" to "첫 마디를 받았을 때",
    "asked" to "질문을 받았을 때",
    "shared" to "상대가 자기 이야기를 할 때",
    "personal" to "개인적인 질문을 받았을 때",
    "praised" to "칭찬을 받았을 때",
    "unknown_topic" to "모르는 화제가 나왔을 때",
    "after_silence" to "침묵이 흐른 뒤",
)

val DISTANCE_LABELS = linkedMapOf("first-meet" to "처음 보는 사이", "familiar-face" to "얼굴만 아는 사이", "daily" to "매일 보는 사이")

/** 리포트를 보여 주려면 최소 이만큼 대화가 있어야 한다 */
const val MIN_CONVERSATIONS = 3

private val WEAK_SHAPES = setOf("safe_reaction", "short_answer", "no_answer", "fact_only")

data class StuckMoment(val context: String, val avgDelayMs: Long, val otherAvgDelayMs: Long?)
data class SituationHabit(val context: String, val shape: String, val count: Int)
data class Tip(val title: String, val detail: String)

data class HabitReport(
    val conversations: Int,
    val answers: Int,
    val shapeCounts: Map<String, Int>,
    /** 리액션 + 한 마디 비율 (0~100) */
    val reactionPlusRate: Int,
    /** 지난주보다 한 마디 얹은 답이 몇 개 늘었나 (지난주 기록이 없으면 null) */
    val reactionPlusDelta: Int?,
    val phrases: List<Pair<String, Int>>,
    val stuck: StuckMoment?,
    val situation: SituationHabit?,
    val badSignals: List<Pair<String, Int>>,
    val goodSignals: List<Pair<String, Int>>,
    /** 관계 거리 → 리액션 + 한 마디 비율 */
    val byDistance: Map<String, Int>,
    val weakestDistance: String?,
    val tip: Tip,
)

/** 최근 7일(this)과 그 전 7일(prev)로 나눠 리포트를 만든다. 대화가 부족하면 null */
fun buildReport(records: List<HabitRecord>, now: Instant = Instant.now()): HabitReport? {
    val weekAgo = now.minus(Duration.ofDays(7))
    val week = records.filter { it.startedAt >= weekAgo }
    val prev = records.filter { it.startedAt < weekAgo }
    if (week.size < MIN_CONVERSATIONS) return null

    val answers = week.flatMap { r -> r.answers.map { it to r.distance } }
    val shapes = answers.groupingBy { it.first.shape }.eachCount()
    val plus = shapes["reaction_plus"] ?: 0
    val rate = if (answers.isEmpty()) 0 else plus * 100 / answers.size
    val prevPlus = prev.sumOf { r -> r.answers.count { it.shape == "reaction_plus" } }

    val phrases = week.flatMap { it.phrases }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }

    // 머리가 하얘지는 순간: 반응 시간이 가장 긴 상황(표본 2개 이상)과 나머지 평균
    val timed = answers.mapNotNull { (a, _) -> a.delayMs?.let { a.context to it } }
    val stuck = timed.groupBy({ it.first }, { it.second })
        .filterValues { it.size >= 2 }
        .maxByOrNull { it.value.average() }
        ?.let { (ctx, delays) ->
            val others = timed.filter { it.first != ctx }.map { it.second }
            StuckMoment(ctx, delays.average().toLong(), others.takeIf { it.isNotEmpty() }?.average()?.toLong())
        }

    // 상황 × 습관: 같은 상황에서 약한 모양이 두 번 이상 나온 조합 중 가장 잦은 것
    val situation = answers.filter { it.first.shape in WEAK_SHAPES && it.first.context != "opening" }
        .groupingBy { it.first.context to it.first.shape }.eachCount()
        .filterValues { it >= 2 }
        .maxByOrNull { it.value }
        ?.let { SituationHabit(it.key.first, it.key.second, it.value) }

    val signals = answers.flatMap { it.first.signals }.groupingBy { it }.eachCount()
    val bad = signals.filterKeys { it !in GOOD_SIGNALS }.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
    val good = signals.filterKeys { it in GOOD_SIGNALS }.entries.sortedByDescending { it.value }.map { it.key to it.value }

    val byDistance = answers.filter { it.second != null }.groupBy({ it.second!! }, { it.first })
        .mapValues { (_, list) -> list.count { it.shape == "reaction_plus" } * 100 / list.size }
    val weakest = byDistance.minByOrNull { it.value }?.key

    return HabitReport(
        conversations = week.size,
        answers = answers.size,
        shapeCounts = shapes,
        reactionPlusRate = rate,
        reactionPlusDelta = if (prev.isEmpty()) null else plus - prevPlus,
        phrases = phrases,
        stuck = stuck,
        situation = situation,
        badSignals = bad,
        goodSignals = good,
        byDistance = byDistance,
        weakestDistance = weakest,
        tip = tipOf(shapes, situation, stuck, signals),
    )
}

/** 이번 주 써볼 한 가지: 가장 두드러진 습관 하나에 맞춘 한 줄 */
private fun tipOf(shapes: Map<String, Int>, situation: SituationHabit?, stuck: StuckMoment?, signals: Map<String, Int>): Tip {
    val top = shapes.filterKeys { it in WEAK_SHAPES || it == "reaction_plus" }.maxByOrNull { it.value }?.key
    return when {
        situation?.context == "praised" -> Tip("칭찬을 받으면 고맙다고 하고 한마디 돌려주기", "\"감사해요, ○○님도 요즘 좋아 보이세요\"처럼요.")
        stuck?.context == "personal" -> Tip("자주 받는 질문의 답을 하나 미리 정해 두기", "\"주말에 뭐 하세요?\"에 할 답 하나면 머뭇거림이 줄어요.")
        (signals["interview_mode"] ?: 0) >= 2 -> Tip("질문 사이에 내 이야기 한 조각 넣기", "질문만 이어지면 면접처럼 느껴질 수 있어요.")
        top == "safe_reaction" -> Tip("맞장구 뒤에 질문 하나 붙이기", "\"아 진짜요?\" 뒤에 \"어떻게 하게 되셨어요?\"처럼요.")
        top == "short_answer" || top == "fact_only" -> Tip("답한 뒤에 이유 한 조각 붙이기", "\"네\" 대신 \"네, 요즘 그게 재밌더라고요\"처럼요.")
        top == "no_answer" -> Tip("막히면 상대 말을 되받아 시간 벌기", "\"아 주말에 캠핑이요?\"처럼 받기만 해도 이어져요.")
        else -> Tip("질문을 받으면 답하고, 같은 질문 돌려주기", "대화를 상대에게 넘겨주는 가장 쉬운 방법이에요.")
    }
}

/** 운전 중에 들려줄 리포트 (숫자는 최소로, 약 40초) */
fun HabitReport.spoken(name: String?): String = buildString {
    append(if (name != null) "${name}님, 지난 일주일 대화 습관이에요. " else "지난 일주일 대화 습관이에요. ")
    append("대화 ${conversations}번에서 한 마디를 얹은 답이 ${shapeCounts["reaction_plus"] ?: 0}개였어요. ")
    reactionPlusDelta?.takeIf { it > 0 }?.let { append("지난주보다 ${it}개 늘었어요. ") }
    phrases.firstOrNull()?.let { append("가장 자주 쓴 말은 ${it.first}예요. ") }
    stuck?.let { append("${CONTEXT_LABELS[it.context]} 조금 머뭇거렸어요. ") }
    append("이번 주는 ${tip.title}, 해 볼까요?")
}
