package com.smalltoktok.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smalltoktok.pleos.ui.theme.Palette

/** 답의 모양 막대 색 (라임 = 목표) */
private val SHAPE_COLORS = mapOf(
    "reaction_plus" to Palette.accent,
    "safe_reaction" to Color(0xFFC9CEC4),
    "question_back" to Palette.ink,
    "self_share" to Color(0xFF4F7A33),
    "short_answer" to Color(0xFF8E948A),
    "fact_only" to Color(0xFFB3B8AE),
    "topic_shift" to Color(0xFF6E7369),
    "no_answer" to Color(0xFFE2E5DF),
)

/**
 * 나의 대화 습관 (시안 smalltalktalk docs/prototypes/habit-report.html).
 * 정차 중: 성공률 → 카드들(답의 모양, 단골 표현, 막히는 순간, 상황×습관, 어색함 신호, 자리별) → 이번 주 써볼 한 가지.
 * 주행 중: 숫자와 그래프 대신 "리포트 듣기" 하나. 점수·등급·"틀렸다"는 쓰지 않는다.
 */
@Composable
fun HabitScreen(vm: HabitViewModel, parked: Boolean, name: String?, onPractice: (distance: String?) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    val report = state.report
    when {
        state.loading -> Box(Modifier.fillMaxSize().padding(32.dp)) { Loading("대화 습관을 모으고 있어요") }
        report == null -> NotEnough(state, onPractice)
        !parked -> Centered {
            Text("지난주 리포트가 나왔어요", style = MaterialTheme.typography.headlineMedium)
            Text("운전 중에는 소리로 들려드려요.", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
            BigButton(if (state.speaking) "멈추기" else "▶ 리포트 듣기", accent = !state.speaking) { vm.toggleSpeak(name) }
        }
        else -> Report(report, state, name, vm, onPractice)
    }
}

@Composable
private fun Report(r: HabitReport, state: HabitState, name: String?, vm: HabitViewModel, onPractice: (String?) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        full {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (name != null) "${name}님의 대화 습관" else "나의 대화 습관", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                Text("최근 7일 · 대화 ${r.conversations}번 · 내 답 ${r.answers}개", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
            }
        }
        full { Hero(r, state.speaking) { vm.toggleSpeak(name) } }

        item {
            HabitCard("내 답의 모양") {
                ShapeBar(r)
                SHAPE_LABELS.forEach { (key, label) ->
                    val n = r.shapeCounts[key] ?: 0
                    if (n > 0) Legend(SHAPE_COLORS[key] ?: Palette.muted, "$label $n")
                }
            }
        }
        item {
            HabitCard("나의 단골 표현") {
                if (r.phrases.isEmpty()) {
                    Sub("아직 반복해서 쓰는 표현이 보이지 않아요.")
                } else {
                    r.phrases.forEach { (p, n) -> ChipLine("“$p”", "${n}번") }
                    Sub("“${r.phrases.first().first}” 뒤에 내 생각 한 조각을 붙이면 리액션 + 한 마디가 돼요.")
                }
            }
        }
        r.stuck?.let { s ->
            item {
                HabitCard("머리가 하얘지는 순간") {
                    Main(CONTEXT_LABELS[s.context] ?: s.context)
                    Meter("첫 마디까지", s.avgDelayMs, maxOf(s.avgDelayMs, s.otherAvgDelayMs ?: 0))
                    s.otherAvgDelayMs?.let { Meter("다른 순간", it, maxOf(s.avgDelayMs, it)) }
                }
            }
        }
        r.situation?.let { s ->
            item {
                HabitCard("상황 × 습관") {
                    Main("${CONTEXT_LABELS[s.context]} ${SHAPE_LABELS[s.shape]}(으)로 끝낼 때가 많아요")
                    Sub("같은 상황에서 ${s.count}번 있었어요.")
                }
            }
        }
        if (r.badSignals.isNotEmpty() || r.goodSignals.isNotEmpty()) {
            item {
                HabitCard("어색함 신호") {
                    r.badSignals.forEach { (k, n) -> ChipLine(SIGNAL_LABELS[k] ?: k, "${n}번") }
                    if (r.goodSignals.isNotEmpty()) {
                        Sub("좋은 신호: " + r.goodSignals.joinToString { (k, n) -> "${SIGNAL_LABELS[k]} ${n}번" })
                    }
                }
            }
        }
        if (r.byDistance.isNotEmpty()) {
            item {
                HabitCard("자리별 리액션 + 한 마디") {
                    DISTANCE_LABELS.forEach { (k, label) -> r.byDistance[k]?.let { Percent(label, it) } }
                    r.weakestDistance?.let { Sub("${DISTANCE_LABELS[it]}에서 더 조심스러워요.") }
                }
            }
        }
        full { TryThis(r) { onPractice(r.weakestDistance) } }
        full { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun Hero(r: HabitReport, speaking: Boolean, onSpeak: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.accent).padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column {
            Text("리액션 + 한 마디", style = MaterialTheme.typography.labelLarge, color = Palette.onAccent)
            Text("${r.reactionPlusRate}%", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Palette.onAccent)
        }
        val delta = r.reactionPlusDelta
        Text(
            when {
                delta == null -> "한 마디를 얹은 답이 ${r.shapeCounts["reaction_plus"] ?: 0}개였어요. 다음 주와 비교해 드릴게요."
                delta > 0 -> "지난주보다 한 마디 얹은 답이 ${delta}개 늘었어요."
                delta == 0 -> "지난주와 비슷하게 한 마디를 얹고 있어요."
                else -> "이번 주는 맞장구가 조금 많았어요. 한 조각만 더 얹어 봐요."
            },
            style = MaterialTheme.typography.titleLarge, color = Palette.onAccent, modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onSpeak,
            modifier = Modifier.height(72.dp),
            shape = RoundedCornerShape(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.ink, contentColor = Palette.surface),
        ) { Text(if (speaking) "멈추기" else "▶ 리포트 듣기", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun TryThis(r: HabitReport, onPractice: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.ink).padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("이번 주 써볼 한 가지", style = MaterialTheme.typography.labelLarge, color = Palette.accent)
            Text(r.tip.title, style = MaterialTheme.typography.titleLarge, color = Palette.surface)
            Text(r.tip.detail, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFB3B8AE))
        }
        Button(
            onClick = onPractice,
            modifier = Modifier.height(80.dp),
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent),
        ) {
            Text(
                "이 습관으로 연습하기" + (r.weakestDistance?.let { " · ${DISTANCE_LABELS[it]}" } ?: ""),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** 대화가 3번이 안 될 때 */
@Composable
private fun NotEnough(state: HabitState, onPractice: (String?) -> Unit) = Centered {
    val left = (MIN_CONVERSATIONS - state.weekConversations).coerceAtLeast(1)
    Text("대화를 ${left}번 더 하면 습관이 보여요", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(MIN_CONVERSATIONS) { i ->
            Box(
                Modifier.width(56.dp).height(14.dp).clip(CircleShape)
                    .background(if (i < state.weekConversations) Palette.ink else Color(0xFFD5DAD1)),
            )
        }
    }
    Text(
        "대화를 마칠 때마다 내 답의 모양과 자주 쓰는 표현을 모아 둘게요.\n대화 내용 전체는 저장하지 않아요.",
        style = MaterialTheme.typography.bodyLarge, color = Palette.muted, textAlign = TextAlign.Center,
    )
    state.error?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    BigButton("오늘의 상대와 대화하기", accent = true) { onPractice(null) }
}

// ---------- 작은 조각 ----------

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Centered(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        Modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun HabitCard(label: String, content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Palette.surface).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.muted)
    content()
}

@Composable
private fun Main(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)

@Composable
private fun Sub(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.muted)

@Composable
private fun ChipLine(text: String, count: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(CircleShape).background(Palette.bg).padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Spacer(Modifier.width(10.dp))
    Text(count, style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
}

@Composable
private fun ShapeBar(r: HabitReport) = Row(Modifier.fillMaxWidth().height(22.dp).clip(CircleShape)) {
    SHAPE_LABELS.keys.forEach { key ->
        val n = r.shapeCounts[key] ?: 0
        if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(SHAPE_COLORS[key] ?: Palette.muted))
    }
}

@Composable
private fun Legend(color: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(14.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(8.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
}

@Composable
private fun Meter(label: String, ms: Long, max: Long) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(130.dp))
    Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(Color(0xFFD5DAD1))) {
        Box(Modifier.fillMaxWidth(if (max > 0) ms.toFloat() / max else 0f).fillMaxHeight().background(Palette.ink))
    }
    Text(String.format(java.util.Locale.KOREA, "%.1f초", ms / 1000.0), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp))
}

@Composable
private fun Percent(label: String, percent: Int) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(170.dp))
    Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(Color(0xFFD5DAD1))) {
        Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().background(Palette.ink))
    }
    Text("$percent%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp))
}
