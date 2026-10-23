package com.smalltoktok.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smalltoktok.pleos.data.Scenario
import com.smalltoktok.pleos.ui.theme.Palette

/** 폰 앱 관계 거리 칩 (packages/shared DISTANCES). 고르지 않아도 된다 */
private val DISTANCES = listOf("first-meet" to "처음 보는 사이", "familiar-face" to "얼굴만 아는 사이", "daily" to "매일 보는 사이")

/**
 * 아쉬운 대화 탭.
 * 털어놓기: 라임 공을 눌러 말한다 → 받아쓴 조각이 쌓인다 → "다 말했어요"로 한 번에 제출.
 * 주행 중에는 받아쓴 글 대신 몇 문장 들었는지만 보여주고, 결과는 소리로 들려준다.
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
            ReflectPhase.Telling, ReflectPhase.Listening -> Telling(state, partial, parked, vm)
            ReflectPhase.Submitting -> {
                Text("이야기를 정리하고 있어요", style = MaterialTheme.typography.headlineMedium)
                Loading("상황을 이해하고, 해볼 만한 한마디를 찾는 중이에요")
                if (parked) Script(state.pieces)
            }
            ReflectPhase.Result -> Result(state, parked, vm, onPractice)
        }
    }
}

@Composable
private fun Telling(state: ReflectState, partial: String, parked: Boolean, vm: ReflectViewModel) {
    val listening = state.phase == ReflectPhase.Listening
    Text("어떤 대화가 아쉬웠나요?", style = MaterialTheme.typography.headlineMedium)
    Text(
        "누구와, 어디서, 무슨 말이 오갔는지 편하게 말해 주세요. 다 말하면 아래 버튼을 눌러요.",
        style = MaterialTheme.typography.bodyLarge, color = Palette.muted,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        RallyBall(listening = listening, onClick = { vm.toggleListening() })
        Spacer(Modifier.width(24.dp))
        Text(
            when {
                listening && parked && partial.isNotEmpty() -> partial
                listening -> "듣고 있어요. 다 말하면 공을 눌러요"
                state.pieces.isEmpty() -> "공을 누르고 말해 보세요"
                else -> "이어서 말하려면 공을 눌러요"
            },
            style = MaterialTheme.typography.titleLarge,
        )
    }
    if (state.pieces.isNotEmpty()) {
        if (parked) Script(state.pieces) else Text("${state.pieces.size}문장 들었어요", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { vm.undoLast() }, modifier = Modifier.height(64.dp)) {
            Text("방금 말한 부분 지우기", style = MaterialTheme.typography.labelLarge)
        }
    }
    if (parked) {
        Text("상대와의 거리 (골라도 되고 안 골라도 돼요)", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DISTANCES.forEach { (slug, label) ->
                Chip(label, selected = state.distance == slug) { vm.setDistance(if (state.distance == slug) null else slug) }
            }
        }
    }
    state.error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.ink) }
    if (state.canSubmit) {
        BigButton("다 말했어요, 연습 만들기", accent = true) { vm.submit() }
    } else if (state.pieces.isNotEmpty()) {
        Text("조금만 더 말해 주세요.", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
    }
}

@Composable
private fun Result(state: ReflectState, parked: Boolean, vm: ReflectViewModel, onPractice: (Scenario) -> Unit) {
    val r = state.result ?: return
    Text("이렇게 해볼 수 있었어요", style = MaterialTheme.typography.headlineMedium)
    // 연습 버튼을 맨 위에 (폰 앱과 같다). 주행 중에도 음성 대화라 바로 시작할 수 있다
    r.scenario?.let { s ->
        BigButton("이 상황으로 연습하기 · ${s.whoShort}", accent = true) { onPractice(s) }
    }
    if (!parked) {
        Text("차를 세우면 결과를 글로 볼 수 있어요.", style = MaterialTheme.typography.titleLarge)
        BigButton("결과 들어보기") { vm.hearAll() }
    } else {
        Card { Text(r.summary, style = MaterialTheme.typography.bodyLarge) }
        r.lines.forEachIndexed { i, l ->
            Card {
                Text("“${l.line}”", style = MaterialTheme.typography.titleLarge)
                Text(l.why, style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
                OutlinedButton(onClick = { vm.hearLine(i) }, modifier = Modifier.height(60.dp)) {
                    Text("들어보기", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    OutlinedButton(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
        Text("다른 이야기 하기", style = MaterialTheme.typography.labelLarge)
    }
}

/** 말풍선 대신 대본처럼 이어 쓴다 */
@Composable
private fun Script(pieces: List<String>) = Card {
    pieces.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun Card(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.surface).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) { content() }

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) = Text(
    label,
    style = MaterialTheme.typography.labelLarge,
    color = if (selected) Palette.onAccent else Palette.ink,
    modifier = Modifier
        .clip(CircleShape)
        .background(if (selected) Palette.accent else Palette.surface)
        .clickable(onClick = onClick)
        .padding(horizontal = 24.dp, vertical = 16.dp),
)
