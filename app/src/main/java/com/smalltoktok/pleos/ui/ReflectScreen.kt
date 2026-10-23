package com.smalltoktok.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smalltoktok.pleos.data.ReflectionResult
import com.smalltoktok.pleos.data.Scenario
import com.smalltoktok.pleos.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 폰 앱 관계 거리 칩 (packages/shared DISTANCES). 고르지 않아도 된다 */
private val DISTANCES = listOf("first-meet" to "처음 보는 사람", "familiar-face" to "얼굴만 아는 사람", "daily" to "매일 보는 사람")

/**
 * 아쉬운 대화 탭 (폰 앱 화면을 모티브로, 2026-10-23).
 * 털어놓기: "아쉬웠던 그 순간, 다시 해 볼까요?" + 라임 공 + 장면 칩 → 받아쓴 조각 → (같이 돌아보기 | 추가 상황 말하기)
 * → 기다리는 동안 내가 한 말과 단계 → 결과(연습하기 맨 위, 이렇게 이해했어요, 요약, 추천 한마디) → 아래에 지난 기록.
 * 주행 중에는 글 대신 몇 문장 들었는지만 보여주고, 결과는 소리로 들려준다.
 */
@Composable
fun ReflectScreen(vm: ReflectViewModel, parked: Boolean, onPractice: (Scenario) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val partial by vm.voice.partial.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (state.phase) {
            ReflectPhase.Telling, ReflectPhase.Listening -> {
                Telling(state, partial, parked, vm)
                if (parked && state.phase == ReflectPhase.Telling && state.past.isNotEmpty()) Past(state.past, vm, onPractice)
            }
            ReflectPhase.Submitting -> Waiting(state, parked)
            ReflectPhase.Result -> Result(state, parked, vm, onPractice)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * 털어놓기. 처음에는 폰 앱의 히어로 카드(제목·안내·라임 공·말할 거리·장면 칩).
 * 한 번 말하고 멈추면 같이 돌아보기 / 추가 상황 말하기 (2026-10-23 피드백: 다 말하지 못해도 이어 붙인다).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Telling(state: ReflectState, partial: String, parked: Boolean, vm: ReflectViewModel) {
    val listening = state.phase == ReflectPhase.Listening
    val first = state.pieces.isEmpty()

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.surface).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (first) "아쉬웠던 그 순간, 다시 해 볼까요?" else "더 하고 싶은 말이 있나요?",
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
        )
        Text(
            if (first) "그때 일을 편하게 말해 주시면, 그 사람과 다시 연습할 수 있게 만들어 드려요. 두서없어도 괜찮아요."
            else "빠진 상황이나 그때 마음을 더 말해도 되고, 이대로 같이 돌아봐도 돼요.",
            style = MaterialTheme.typography.bodyLarge, color = Palette.muted, textAlign = TextAlign.Center,
        )
        if (first || listening) {
            RallyBall(listening = listening, onClick = { vm.toggleListening() }, animate = parked)
            Text(
                when {
                    listening && parked && partial.isNotEmpty() -> partial
                    listening -> "듣고 있어요. 다 말했으면 공을 눌러요"
                    else -> "눌러서 말하기"
                },
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            if (first && !listening) {
                Text("누구와 · 어디서 · 무슨 말이 오갔는지 · 어느 순간이 아쉬웠는지", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
            }
        }
        // 어디서부터 말할지 막힐 때만 쓰는 보조 입구. 첫마디만 채우고 나머지는 내 이야기로 (글이라 정차 중에만)
        if (first && !listening && parked) {
            Text("이런 순간이었나요?", style = MaterialTheme.typography.labelLarge, color = Palette.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.scenes.forEach { s -> Chip(s.label, selected = false, bordered = true) { vm.useScene(s) } }
            }
            TextButton(onClick = { vm.moreScenes() }) { Text("다른 순간 보기", style = MaterialTheme.typography.labelLarge, color = Palette.muted) }
        }
    }

    if (!first) {
        if (parked) Script(state.pieces) else Text("지금까지 ${state.pieces.size}문장 들었어요", style = MaterialTheme.typography.titleLarge)
        if (!listening) {
            if (state.canSubmit) {
                BigButton(if (state.replacing != null) "고친 내용으로 다시 만들기" else "같이 돌아보기", accent = true) { vm.submit() }
            } else {
                Text("${state.need}자만 더 들려주세요", style = MaterialTheme.typography.titleLarge, color = Palette.muted)
            }
            BigButton("추가 상황 말하기") { vm.toggleListening() }
            OutlinedButton(onClick = { vm.undoLast() }, modifier = Modifier.height(64.dp)) {
                Text("방금 말한 부분 지우기", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (parked && !listening) {
        Text("어떤 사이였어요? 골라도 되고 안 골라도 돼요", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DISTANCES.forEach { (slug, label) ->
                Chip(label, selected = state.distance == slug) { vm.setDistance(if (state.distance == slug) null else slug) }
            }
        }
    }
    state.error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.ink) }
    if (!listening) {
        Text("나만 볼 수 있고, 아래 기록에서 언제든 지울 수 있어요.", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
    }
}

/** 기다리는 동안: 단계 문구가 바뀌고, 내가 한 말을 대본으로 보여준다 (폰 앱과 같다) */
@Composable
private fun Waiting(state: ReflectState, parked: Boolean) {
    Text("같이 돌아보는 중이에요", style = MaterialTheme.typography.headlineMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WAIT_STEPS.indices.forEach { i ->
            Box(
                Modifier.width(48.dp).height(12.dp).clip(CircleShape)
                    .background(if (i <= state.waitStep) Palette.ink else Color(0xFFD5DAD1)),
            )
        }
    }
    Text(WAIT_STEPS[state.waitStep], style = MaterialTheme.typography.titleLarge)
    if (parked && state.pieces.isNotEmpty()) {
        Text("내가 한 이야기", style = MaterialTheme.typography.labelLarge, color = Palette.muted)
        Script(state.pieces)
    }
}

@Composable
private fun Result(state: ReflectState, parked: Boolean, vm: ReflectViewModel, onPractice: (Scenario) -> Unit) {
    val r = state.result ?: return
    // 가장 하고 싶은 행동(다시 연습)이 먼저 보이게 맨 위에 둔다. 주행 중에도 음성 대화라 바로 시작할 수 있다
    r.scenario?.let { s ->
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.ink).clickable { onPractice(s) }.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("연습 상황을 만들었어요", style = MaterialTheme.typography.labelLarge, color = Palette.accent)
            Text("이 상황 다시 해보기 →", style = MaterialTheme.typography.headlineMedium, color = Palette.surface)
            Text("상대가 그때처럼 말을 걸어요. 이번엔 한 마디를 얹어 보세요.", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFB3B8AE))
        }
    }
    if (!parked) {
        Text("차를 세우면 결과를 글로 볼 수 있어요.", style = MaterialTheme.typography.titleLarge)
        BigButton("결과 들어보기") { vm.hearAll() }
    } else {
        // 모델이 이해한 상대·장소. 되묻지 않고, 다르면 고쳐서 다시 만들게 한다 (대본 형식)
        r.scenario?.let { s ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(2.dp, Color(0xFFD5DAD1), RoundedCornerShape(24.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("이렇게 이해했어요", style = MaterialTheme.typography.labelLarge, color = Palette.muted)
                ScriptRow("상대", s.who)
                ScriptRow("장소", s.place)
                TextButton(onClick = { vm.editAndRetry(r) }) {
                    Text("다르게 이해했다면 고쳐서 다시 만들기", style = MaterialTheme.typography.labelLarge, color = Palette.ink)
                }
            }
        }
        Card { Text(r.summary, style = MaterialTheme.typography.bodyLarge) }
        if (r.lines.isNotEmpty()) {
            Text("그때 이렇게 말해볼 수 있었어요", style = MaterialTheme.typography.titleLarge)
            Lines(r, vm)
        }
    }
    OutlinedButton(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
        Text("다른 이야기 털어놓기", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Lines(r: ReflectionResult, vm: ReflectViewModel) = r.lines.forEachIndexed { i, l ->
    Card {
        Text("“${l.line}”", style = MaterialTheme.typography.titleLarge)
        Text(l.why, style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
        OutlinedButton(onClick = { vm.hearLine(r, i) }, modifier = Modifier.height(60.dp)) {
            Text("들어보기", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 지난 아쉬운 대화: 날짜·요약, 펼치면 추천 한마디. 바로 다시 해보기 / 지우기 */
@Composable
private fun Past(past: List<ReflectionResult>, vm: ReflectViewModel, onPractice: (Scenario) -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ReflectionResult?>(null) }
    Spacer(Modifier.height(8.dp))
    Text("지난 아쉬운 대화", style = MaterialTheme.typography.titleLarge)
    past.forEach { r ->
        val expanded = open == r.reflectionId
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.surface)
                .clickable { open = if (expanded) null else r.reflectionId }.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(dateLabel(r.createdAt), style = MaterialTheme.typography.labelLarge, color = Palette.muted)
            Text(
                r.summary, style = MaterialTheme.typography.bodyLarge,
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
            )
            if (expanded) r.lines.forEach { l -> Text("“${l.line}”", style = MaterialTheme.typography.titleLarge) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                r.scenario?.let { s -> Chip("다시 해보기", selected = true) { onPractice(s) } }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { deleting = r }) { Text("지우기", style = MaterialTheme.typography.labelLarge, color = Palette.muted) }
            }
        }
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("이 이야기를 지울까요?") },
            text = {
                Text(
                    if (r.scenario != null) "털어놓은 내용과 이 이야기로 만든 연습이 함께 지워져요."
                    else "털어놓은 내용이 지워져요.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.deletePast(r); deleting = null }) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

private fun dateLabel(at: Instant?): String {
    if (at == null) return ""
    val days = Duration.between(at, Instant.now()).toDays()
    return when {
        days <= 0 -> "오늘"
        days == 1L -> "어제"
        days < 7 -> "${days}일 전"
        else -> at.atZone(ZoneId.of("Asia/Seoul")).let { "${it.monthValue}월 ${it.dayOfMonth}일" }
    }
}

/** 말풍선 대신 대본처럼 이어 쓴다 */
@Composable
private fun Script(pieces: List<String>) = Card {
    pieces.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun ScriptRow(name: String, text: String) = Row {
    Text(name, style = MaterialTheme.typography.labelLarge, color = Palette.muted, modifier = Modifier.width(80.dp))
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun Card(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.surface).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) { content() }

@Composable
internal fun Chip(label: String, selected: Boolean, bordered: Boolean = false, onClick: () -> Unit) = Text(
    label,
    style = MaterialTheme.typography.labelLarge,
    color = if (selected) Palette.onAccent else Palette.ink,
    modifier = Modifier
        .clip(CircleShape)
        .background(if (selected) Palette.accent else if (bordered) Palette.bg else Palette.surface)
        .clickable(onClick = onClick)
        .padding(horizontal = 24.dp, vertical = 16.dp),
)
