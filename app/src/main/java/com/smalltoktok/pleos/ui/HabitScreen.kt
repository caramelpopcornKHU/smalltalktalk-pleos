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

/**
 * 나의 대화 습관 v2 (2026-10-26): 영상 노트에서 뽑은 스몰토크 기준 5가지 + 편안함 지키기를 지표로 보여준다.
 * 정차 중: 가장 잘한 것·먼저 연습할 것 → 기준별 해낸 비율 → 편안함·단골 표현·막히는 순간 → 이번 주 써볼 한 가지.
 * 읽어 주기 칩 두 개: "리포트 듣기"(평범하게)와 "💣 팩트폭행"(반말 친구가 팩트로 짚어 줌).
 * 주행 중: 숫자와 그래프 대신 읽어 주기 칩만. 점수·등급·"틀렸다"는 쓰지 않는다(팩트폭행도 행동만 짚는다).
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
            ReadChips(state, onDark = false) { vm.read(it, name) }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.muted) }
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
                Text(
                    "최근 7일 · 대화 ${r.conversations}번 · 내 답 ${r.answers}개" +
                        (if (r.hardConversations > 0) " · 어려운 상대 ${r.hardConversations}번" else ""),
                    style = MaterialTheme.typography.bodyLarge, color = Palette.muted,
                )
            }
        }
        full { Hero(r, state) { vm.read(it, name) } }
        state.roastText?.let { text -> full { RoastCard(text) } }

        full {
            HabitCard("스몰토크 기준 · 해낸 비율") {
                r.criteria.forEach { c -> CriterionRow(c, highlight = c.key == r.weakest?.key) }
                Sub("해당하는 답만 셌어요. 예를 들어 질문을 받지 않은 답은 \"초대 받기\"에서 빼요.")
            }
        }
        item {
            HabitCard("편안함 지키기") {
                if (r.slips.isEmpty()) {
                    Main("불편하게 만드는 실수가 없었어요")
                    Sub("질문 폭격, 평가성 칭찬, 민감한 주제, 자랑이 보이지 않았어요.")
                } else {
                    r.slips.forEach { (k, n) -> ChipLine(SLIP_LABELS[k] ?: k, "${n}번") }
                    Sub("스몰토크의 목적은 상대가 나를 편하게 느끼는 거예요.")
                }
            }
        }
        item {
            HabitCard("나의 단골 표현") {
                if (r.phrases.isEmpty()) {
                    Sub("아직 반복해서 쓰는 표현이 보이지 않아요.")
                } else {
                    r.phrases.forEach { (p, n) -> ChipLine("“$p”", "${n}번") }
                    Sub("“${r.phrases.first().first}” 뒤에 이유나 내 이야기 한 조각을 붙여 봐요.")
                }
            }
        }
        r.stuck?.let { s ->
            item {
                HabitCard("머리가 하얘지는 순간") {
                    Main(CONTEXT_LABELS[s.context] ?: s.context)
                    Meter("첫 마디까지", s.avgDelayMs, maxOf(s.avgDelayMs, s.otherAvgDelayMs ?: 0))
                    s.otherAvgDelayMs?.let { Meter("다른 순간", it, maxOf(s.avgDelayMs, it)) }
                    if (r.silent > 0) Sub("말하지 못한 답이 ${r.silent}번 있었어요. 막히면 상대 말을 되받기만 해도 이어져요.")
                }
            }
        }
        r.weakest?.let { w -> full { TryThis(w, r.weakestDistance) { onPractice(r.weakestDistance) } } }
        full { Spacer(Modifier.height(8.dp)) }
    }
}

/** 맨 위: 가장 잘한 것과 가장 먼저 연습할 것, 읽어 주기 칩 두 개 */
@Composable
private fun Hero(r: HabitReport, state: HabitState, onRead: (ReadMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.accent).padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            r.best?.let {
                Text("가장 잘한 것", style = MaterialTheme.typography.labelLarge, color = Palette.onAccent)
                Text("${it.info.label} ${it.rate}%", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Palette.onAccent)
            }
            r.weakest?.takeIf { it.key != r.best?.key }?.let {
                Text(
                    "먼저 연습할 것은 ${it.info.label}(${it.rate}%)이에요. " +
                        (it.prevRate?.let { p -> if (it.rate > p) "지난주보다 올랐어요." else "" } ?: ""),
                    style = MaterialTheme.typography.titleLarge, color = Palette.onAccent,
                )
            }
        }
        ReadChips(state, onDark = true, onRead)
    }
}

/** 읽어 주기 칩 두 개. 읽는 중인 칩은 "멈추기"로 바뀐다 */
@Composable
private fun ReadChips(state: HabitState, onDark: Boolean, onRead: (ReadMode) -> Unit) = Column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    ReadButton(if (state.reading == ReadMode.Plain) "멈추기" else "▶ 리포트 듣기", onDark) { onRead(ReadMode.Plain) }
    ReadButton(
        when {
            state.roastLoading -> "팩트 모으는 중…"
            state.reading == ReadMode.Roast -> "멈추기"
            else -> "💣 팩트폭행"
        },
        onDark,
    ) { onRead(ReadMode.Roast) }
}

@Composable
private fun ReadButton(label: String, onDark: Boolean, onClick: () -> Unit) = Button(
    onClick = onClick,
    modifier = Modifier.width(260.dp).height(72.dp),
    shape = RoundedCornerShape(36.dp),
    colors = if (onDark) {
        ButtonDefaults.buttonColors(containerColor = Palette.ink, contentColor = Palette.surface)
    } else {
        ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent)
    },
) { Text(label, style = MaterialTheme.typography.labelLarge) }

/** 마지막으로 들은 팩트폭행 (정차 중 다시 읽을 수 있게) */
@Composable
private fun RoastCard(text: String) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.ink).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text("💣 친구의 팩트폭행", style = MaterialTheme.typography.labelLarge, color = Palette.accent)
    Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 34.sp), color = Palette.surface)
}

@Composable
private fun CriterionRow(c: CriterionStat, highlight: Boolean) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(c.info.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.width(140.dp))
        Box(Modifier.weight(1f).height(14.dp).clip(CircleShape).background(Color(0xFFD5DAD1))) {
            if (c.total > 0) {
                Box(Modifier.fillMaxWidth(c.rate / 100f).fillMaxHeight().background(if (highlight) Palette.ink else Palette.accent))
            }
        }
        Text(
            if (c.total == 0) "해당 없음" else "${c.hit}/${c.total}",
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(110.dp).padding(start = 12.dp),
        )
    }
    Text(c.info.hit, style = MaterialTheme.typography.bodyLarge, color = Palette.muted, modifier = Modifier.padding(start = 140.dp))
}

@Composable
private fun TryThis(w: CriterionStat, distance: String?, onPractice: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.ink).padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("이번 주 써볼 한 가지 · ${w.info.label}", style = MaterialTheme.typography.labelLarge, color = Palette.accent)
            Text(w.info.tip, style = MaterialTheme.typography.titleLarge, color = Palette.surface)
        }
        Button(
            onClick = onPractice,
            modifier = Modifier.height(80.dp),
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent),
        ) {
            Text("이 습관으로 연습하기" + (distance?.let { " · ${DISTANCE_LABELS[it]}" } ?: ""), style = MaterialTheme.typography.labelLarge)
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
        "대화를 마칠 때마다 스몰토크 기준(받아 주기, 점프 포인트, 여는 질문, 내 디테일, 초대 받기)으로\n내 답을 모아 둘게요. 대화 내용 전체는 저장하지 않아요.",
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
private fun Meter(label: String, ms: Long, max: Long) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(130.dp))
    Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(Color(0xFFD5DAD1))) {
        Box(Modifier.fillMaxWidth(if (max > 0) ms.toFloat() / max else 0f).fillMaxHeight().background(Palette.ink))
    }
    Text(String.format(java.util.Locale.KOREA, "%.1f초", ms / 1000.0), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp))
}
