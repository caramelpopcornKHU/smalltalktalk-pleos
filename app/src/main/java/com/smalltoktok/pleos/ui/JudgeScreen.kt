package com.smalltoktok.pleos.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smalltoktok.pleos.ui.theme.Palette

/** B 쪽 공 색. 포인트 색은 라임 하나지만 A·B를 구분해야 해서 연한 하늘색을 예외로 쓴다(시안에서 확인) */
private val SideB = Color(0xFFC9D6F2)
private val OnSideB = Color(0xFF1C2B4A)

/**
 * AI 판사 탭 (2026-10-26, 시안 smalltalktalk docs/prototypes/ai-judge.html).
 * 위: 논쟁 주제 공 / 가운데: 왼쪽 A 공 · 저울과 판결 버튼 · 오른쪽 B 공 / 판결문은 아래에 떠오르고 판사 목소리로 읽는다.
 * 넓은 창은 가로 3칸, 좁은 창(상황판 옆)은 세로로 쌓는다. 주행 중에도 공·버튼은 그대로 크게 두고,
 * 판결문은 제목만 보여 주고 소리로 읽는다(Pleos 주행 규제: 긴 글은 정차 중에만).
 */
@Composable
fun JudgeScreen(vm: JudgeViewModel, parked: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp)) {
        val wide = maxWidth > 900.dp
        Column(
            Modifier.fillMaxSize().then(if (wide) Modifier else Modifier.verticalScroll(rememberScrollState())),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TopicRow(state, vm)
            if (wide) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Side(JudgeSlot.A, state, vm, Modifier.weight(1f).fillMaxHeight())
                    Bench(state, vm, Modifier.width(240.dp).fillMaxHeight())
                    Side(JudgeSlot.B, state, vm, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Side(JudgeSlot.A, state, vm, Modifier.fillMaxWidth().height(280.dp))
                Side(JudgeSlot.B, state, vm, Modifier.fillMaxWidth().height(280.dp))
                Bench(state, vm, Modifier.fillMaxWidth())
            }
        }
        if (state.verdict != null) {
            VerdictCard(state, parked, vm, Modifier.align(Alignment.BottomCenter).widthIn(max = 900.dp).fillMaxWidth().padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun TopicRow(state: JudgeState, vm: JudgeViewModel) {
    val listening = state.listening == JudgeSlot.Topic
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.surface).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Ball("주제", Palette.ink, Palette.surface, listening, size = 76, description = "논쟁 주제 말하기") { vm.toggle(JudgeSlot.Topic) }
        Text("오늘의 논쟁", style = MaterialTheme.typography.labelLarge, color = Palette.muted)
        Text(
            state.topic.ifEmpty { if (listening) "듣고 있어요…" else "공을 누르고 논쟁 주제를 말해 주세요" },
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = if (state.topic.isEmpty()) Color(0xFFA6ABA2) else Palette.ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Side(slot: JudgeSlot, state: JudgeState, vm: JudgeViewModel, modifier: Modifier) {
    val isA = slot == JudgeSlot.A
    val claim = if (isA) state.a else state.b
    val rebuttal = if (isA) state.appealA else state.appealB
    val listening = state.listening == slot
    val winner = state.verdict?.winner
    val won = winner == (if (isA) "a" else "b")
    val lost = winner != null && winner != "none" && !won
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Palette.surface)
                .then(if (won) Modifier.border(5.dp, Palette.accent, RoundedCornerShape(28.dp)) else Modifier)
                .alpha(if (lost) 0.55f else 1f)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isA) "A" else "B", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    color = if (isA) Palette.onAccent else OnSideB,
                    modifier = Modifier.clip(CircleShape).background(if (isA) Palette.accent else SideB).padding(horizontal = 14.dp, vertical = 4.dp),
                )
                Text(if (isA) "왼쪽 분" else "오른쪽 분", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    claim.ifEmpty { if (listening && !state.appealing) "듣고 있어요…" else "공을 누르고 주장을 말해 주세요" },
                    style = MaterialTheme.typography.titleLarge.copy(lineHeight = 34.sp),
                    color = if (claim.isEmpty()) Color(0xFFA6ABA2) else Palette.ink,
                    maxLines = if (state.appealing) 3 else 6, overflow = TextOverflow.Ellipsis,
                )
                if (state.appealing) {
                    Text(
                        "반박 | " + rebuttal.ifEmpty { if (listening) "듣고 있어요…" else "공을 눌러 반박해요" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (rebuttal.isEmpty()) Palette.muted else Palette.ink,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Ball(
                    if (isA) "A" else "B",
                    if (isA) Palette.accent else SideB, if (isA) Palette.onAccent else OnSideB,
                    listening, size = 96, description = if (isA) "A 주장 말하기" else "B 주장 말하기",
                ) { vm.toggle(slot) }
                Text(
                    when {
                        listening -> "듣고 있어요. 다 말하면 공을 눌러요"
                        state.appealing -> "눌러서 반박하기"
                        claim.isNotEmpty() -> "다시 말하려면 눌러요"
                        else -> "눌러서 말하기"
                    },
                    style = MaterialTheme.typography.bodyLarge, color = Palette.muted,
                )
            }
        }
        if (won) {
            Text(
                "승소", color = Palette.surface, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp).clip(CircleShape).background(Palette.ink).padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/** 가운데: 저울(판결 중엔 흔들림) + 판결 버튼 */
@Composable
private fun Bench(state: JudgeState, vm: JudgeViewModel, modifier: Modifier) {
    val swing = rememberInfiniteTransition(label = "scales")
    val angle by swing.animateFloat(-14f, 14f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "angle")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(140.dp).clip(CircleShape).background(Palette.ink), contentAlignment = Alignment.Center) {
            Box(Modifier.rotate(if (state.judging) angle else 0f)) { JudgeIcon(Palette.surface, size = 76.dp) }
        }
        Button(
            onClick = { vm.judge() },
            enabled = state.ready && !state.judging,
            modifier = Modifier.fillMaxWidth().height(88.dp),
            shape = RoundedCornerShape(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.accent, contentColor = Palette.onAccent,
                disabledContainerColor = Color(0xFFCDD2C9), disabledContentColor = Color(0xFF7C8178),
            ),
        ) {
            Text(
                when {
                    state.judging -> "판결 중…"
                    state.appealing -> "재심 판결!"
                    else -> "판결!"
                },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            )
        }
        Text(
            when {
                state.error != null -> state.error
                state.judging -> "판사가 기록을\n살펴보고 있어요"
                state.appealing -> "반박을 한 쪽이라도 받으면\n다시 판결해요"
                state.ready -> "준비됐어요.\n판결을 눌러 보세요"
                else -> "주제와 두 사람의 주장이\n모이면 판결할 수 있어요"
            },
            style = MaterialTheme.typography.bodyLarge, color = Palette.muted, textAlign = TextAlign.Center,
        )
    }
}

/** 판결문: 정차 중엔 전문, 주행 중엔 제목만(소리로 읽는다). 재심은 한 번만 */
@Composable
private fun VerdictCard(state: JudgeState, parked: Boolean, vm: JudgeViewModel, modifier: Modifier) {
    val v = state.verdict ?: return
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(Palette.ink).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (state.appealed) "재심 판결" else "판결", color = Palette.onAccent, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(CircleShape).background(Palette.accent).padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Text(v.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Palette.surface)
        }
        if (parked) {
            Text(v.reason, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 34.sp), color = Color(0xFFD7DBD3))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.appealed && v.winner != "none") {
                Button(
                    onClick = { vm.appeal() }, modifier = Modifier.height(72.dp), shape = RoundedCornerShape(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent),
                ) { Text("재심 청구", style = MaterialTheme.typography.labelLarge) }
            }
            OutlinedButton(onClick = { vm.replay() }, modifier = Modifier.height(72.dp), shape = RoundedCornerShape(36.dp)) {
                Text("다시 듣기", style = MaterialTheme.typography.labelLarge, color = Palette.surface)
            }
            OutlinedButton(onClick = { vm.reset() }, modifier = Modifier.height(72.dp), shape = RoundedCornerShape(36.dp)) {
                Text("새 논쟁", style = MaterialTheme.typography.labelLarge, color = Palette.surface)
            }
        }
    }
}

/** 말 받는 공. 듣는 중엔 숨 쉬듯 커졌다 작아진다 */
@Composable
private fun Ball(label: String, color: Color, onColor: Color, listening: Boolean, size: Int, description: String, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "ball")
    val s by pulse.animateFloat(1f, if (listening) 1.1f else 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Box(
        Modifier.size(size.dp).scale(s).clip(CircleShape).background(color)
            .then(if (listening) Modifier.border(5.dp, Palette.ink.copy(alpha = 0.35f), CircleShape) else Modifier)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = onColor, fontWeight = FontWeight.ExtraBold, fontSize = if (size >= 90) 26.sp else 18.sp)
    }
}
