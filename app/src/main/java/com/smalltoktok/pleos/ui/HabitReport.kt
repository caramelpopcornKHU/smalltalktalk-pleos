package com.smalltoktok.pleos.ui

import com.smalltoktok.pleos.data.HabitRecord
import java.time.Duration
import java.time.Instant

// 나의 대화 습관 리포트 계산.
// v2 (2026-10-26): 영상 노트 10개에서 뽑은 평가 기준(smalltalktalk docs/conversation-principles.md 2절)을 지표로 쓴다.
// 서버가 대화마다 저장한 요약 태그(내 답마다 기준 hit/miss/na, 실수)를 이 기기에서 모아 센다. 모델을 부르지 않는다.
// 이름과 고치는 법은 smalltalktalk packages/shared/src/habits.ts의 CRITERIA·CRITERIA_GUIDE·SLIPS와 같다.

data class CriterionInfo(val label: String, val hit: String, val tip: String)

val CRITERIA = linkedMapOf(
    "reaction" to CriterionInfo(
        "받아 주기", "상대 말에 이유가 담긴 반응",
        "감탄 뒤에 이유 한 마디: \"우와, 요즘 주변에서도 많이 하던데 유행인가 봐요.\"",
    ),
    "jump" to CriterionInfo(
        "점프 포인트", "상대 말 속 단어를 집어 이어가기",
        "상대 말에서 단어 하나만 집어 되물어요: \"올라오셨다고요? 고향이 어디세요?\"",
    ),
    "question" to CriterionInfo(
        "여는 질문", "경험·감정·추측 질문",
        "확인 대신 추측으로: \"더웠죠?\" 말고 \"아직 좀 더웠겠는데요?\"",
    ),
    "detail" to CriterionInfo(
        "내 디테일", "나만의 사소한 이야기 한 조각",
        "나만 아는 사소한 이야기 한 조각: \"저는 월요일마다 아르기닌을 챙겨 먹어요.\"",
    ),
    "invite" to CriterionInfo(
        "초대 받기", "질문·농담을 풀어서 답하기",
        "\"재밌어요?\"는 더 얘기해 달라는 초대예요. \"네\" 대신 한 장면을 풀어 줘요.",
    ),
)

val SLIP_LABELS = mapOf(
    "interview" to "질문 폭격",
    "evaluation" to "평가성 칭찬",
    "sensitive" to "민감한 주제",
    "own_interest" to "상대 관심 무시한 내 관심사",
    "brag" to "자랑",
)

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

/** 기준 하나의 이번 주 결과. 해당 없는 답(na)은 빼고 센다 */
data class CriterionStat(val key: String, val hit: Int, val total: Int, val prevRate: Int?) {
    val info get() = CRITERIA.getValue(key)
    val rate get() = if (total == 0) 0 else hit * 100 / total
}

data class StuckMoment(val context: String, val avgDelayMs: Long, val otherAvgDelayMs: Long?)

data class HabitReport(
    val conversations: Int,
    val answers: Int,
    /** 기준 1~5 (해당 답이 하나도 없던 기준은 total 0) */
    val criteria: List<CriterionStat>,
    /** 가장 잘한 기준 / 가장 먼저 연습할 기준 (해당 답 2개 이상인 기준 중) */
    val best: CriterionStat?,
    val weakest: CriterionStat?,
    /** 기준 6 편안함 지키기: 실수 → 횟수 */
    val slips: List<Pair<String, Int>>,
    val silent: Int,
    val phrases: List<Pair<String, Int>>,
    val stuck: StuckMoment?,
    /** 어려운 상대(상)와 한 대화 수 */
    val hardConversations: Int,
    /** 기준을 가장 덜 해낸 관계 거리 (이 습관으로 연습하기) */
    val weakestDistance: String?,
)

/** 최근 7일(this)과 그 전 7일(prev)로 나눠 리포트를 만든다. 대화가 부족하면 null */
fun buildReport(records: List<HabitRecord>, now: Instant = Instant.now()): HabitReport? {
    val weekAgo = now.minus(Duration.ofDays(7))
    val week = records.filter { it.startedAt >= weekAgo }
    val prev = records.filter { it.startedAt < weekAgo }
    if (week.size < MIN_CONVERSATIONS) return null

    val answers = week.flatMap { r -> r.answers.map { it to r.distance } }
    val prevAnswers = prev.flatMap { it.answers }
    fun count(list: List<com.smalltoktok.pleos.data.HabitAnswer>, key: String, mark: String) = list.count { it.criteria[key] == mark }

    val mine = answers.map { it.first }
    val criteria = CRITERIA.keys.map { key ->
        val hit = count(mine, key, "hit")
        val total = hit + count(mine, key, "miss")
        val prevHit = count(prevAnswers, key, "hit")
        val prevTotal = prevHit + count(prevAnswers, key, "miss")
        CriterionStat(key, hit, total, if (prevTotal >= 2) prevHit * 100 / prevTotal else null)
    }
    val rated = criteria.filter { it.total >= 2 }

    val phrases = week.flatMap { it.phrases }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }

    // 머리가 하얘지는 순간: 반응 시간이 가장 긴 상황(표본 2개 이상)과 나머지 평균
    val timed = mine.mapNotNull { a -> a.delayMs?.let { a.context to it } }
    val stuck = timed.groupBy({ it.first }, { it.second })
        .filterValues { it.size >= 2 }
        .maxByOrNull { it.value.average() }
        ?.let { (ctx, delays) ->
            val others = timed.filter { it.first != ctx }.map { it.second }
            StuckMoment(ctx, delays.average().toLong(), others.takeIf { it.isNotEmpty() }?.average()?.toLong())
        }

    // 자리별: 기준 해낸 비율(전체 hit / hit+miss)이 가장 낮은 관계 거리
    val weakestDistance = answers.filter { it.second != null }.groupBy({ it.second!! }, { it.first })
        .mapValues { (_, list) ->
            val hit = list.sumOf { a -> a.criteria.values.count { it == "hit" } }
            val total = hit + list.sumOf { a -> a.criteria.values.count { it == "miss" } }
            if (total == 0) 100 else hit * 100 / total
        }
        .minByOrNull { it.value }?.key

    return HabitReport(
        conversations = week.size,
        answers = mine.size,
        criteria = criteria,
        best = rated.maxByOrNull { it.rate },
        weakest = rated.minByOrNull { it.rate },
        slips = mine.flatMap { it.slips }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value },
        silent = mine.count { it.shape == "no_answer" },
        phrases = phrases,
        stuck = stuck,
        hardConversations = week.count { it.difficulty == "hard" },
        weakestDistance = weakestDistance,
    )
}

/** 평범한 리포트 읽기 (존댓말, 숫자는 최소로, 약 40초) */
fun HabitReport.spoken(name: String?): String = buildString {
    append(if (name != null) "${name}님, 지난 일주일 대화 습관이에요. " else "지난 일주일 대화 습관이에요. ")
    append("대화 ${conversations}번, 내 답 ${answers}개를 스몰토크 기준으로 봤어요. ")
    best?.let { append("가장 잘한 건 ${it.info.label}예요. ${it.total}번 중 ${it.hit}번 해냈어요. ") }
    weakest?.takeIf { it.key != best?.key }?.let { append("가장 먼저 연습할 건 ${it.info.label}예요. ${it.info.tip} ") }
    slips.firstOrNull()?.let { (k, n) -> append("${SLIP_LABELS[k] ?: k}이 ${n}번 있었어요. ") }
    phrases.firstOrNull()?.let { append("가장 자주 쓴 말은 ${it.first}예요. ") }
    append("이번 주도 한 마디씩 얹어 봐요.")
}
