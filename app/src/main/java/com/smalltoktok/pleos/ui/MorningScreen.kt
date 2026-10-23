package com.smalltoktok.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smalltoktok.pleos.data.MorningCard
import com.smalltoktok.pleos.ui.theme.Palette

/**
 * 아침 카드 탭. 맨 위 "출근길 브리핑 듣기"가 카드를 차례로 읽어 준다.
 * 주행 중에는 브리핑 버튼과 지금 읽는 카드 소재만, 정차 중에는 카드 목록과 말투 토글.
 */
@Composable
fun MorningScreen(vm: MorningViewModel, parked: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        when {
            state.loading -> Loading("오늘의 아침 카드를 불러오고 있어요")
            state.cards.isEmpty() -> {
                Text(state.error ?: "오늘 아침 카드가 아직 없어요.", style = MaterialTheme.typography.titleLarge)
                BigButton("다시 불러오기") { vm.load() }
            }
            else -> {
                val briefing = state.briefing
                if (briefing != null) {
                    BigButton("브리핑 멈추기") { vm.stopBriefing() }
                } else {
                    // 추천 듣기(짧은 출근길용 3장) / 전부 듣기
                    val picks = vm.recommended(state.cards).size
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { BigButton("추천 듣기 · ${picks}장", accent = true) { vm.toggleBriefing(all = false) } }
                        Box(Modifier.weight(1f)) { BigButton("전부 듣기 · ${state.cards.size}장") { vm.toggleBriefing(all = true) } }
                    }
                }
                if (briefing != null) {
                    val c = state.cards[briefing]
                    Text(MORNING_CATEGORY_LABELS[c.category] ?: "", style = MaterialTheme.typography.titleLarge, color = Palette.muted)
                    Text(c.topic, style = MaterialTheme.typography.headlineMedium)
                }
                if (!parked) {
                    if (briefing == null) Text("운전 중에는 소리로 들려드려요.", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Chip("동료에게", state.tone == Tone.Coworker) { vm.setTone(Tone.Coworker) }
                        Chip("처음 보는 사람에게", state.tone == Tone.Stranger) { vm.setTone(Tone.Stranger) }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        itemsIndexed(state.cards, key = { _, c -> c.id }) { i, c -> MorningCardView(c, state.tone, reading = i == briefing) }
                    }
                }
            }
        }
    }
}

/** 카드 = 소재 한 줄 / 던지는 말 / 상대가 모르거나 반응이 짧을 때 이어가는 말 */
@Composable
private fun MorningCardView(card: MorningCard, tone: Tone, reading: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.surface)
            .then(if (reading) Modifier.border(4.dp, Palette.accent, RoundedCornerShape(24.dp)) else Modifier)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(MORNING_CATEGORY_LABELS[card.category] ?: card.category, style = MaterialTheme.typography.labelLarge, color = Palette.muted)
        Text(card.topic, style = MaterialTheme.typography.bodyLarge)
        Text("“${card.line(tone)}”", style = MaterialTheme.typography.titleLarge)
        Text("이어서: ${card.bridge}", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
        card.sourceName?.let { Text("출처 $it", style = MaterialTheme.typography.bodyLarge, color = Palette.muted) }
    }
}
