package com.smalltoktok.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smalltoktok.pleos.data.MorningCard
import com.smalltoktok.pleos.ui.theme.Palette

/**
 * 아침 카드 탭 (2026-10-26 전문가 3명 합의안: 차량 UX · 정보 설계 · 음성 UX).
 * 정차 중 넓은 창: 위 한 줄(추천 듣기 · 전부 듣기 · 말투 세그먼트) → 맨 앞 "오늘의 한 장"(2칸, 브리핑 중엔 플레이어)
 *   → 나머지 카드 3열(앞면은 소재 + 던지는 말만, 상대 반응별 두 갈래는 펼치기) → 아래 "스몰토크 잘하는 법" 가로 줄.
 * 주행 중·좁은 창: 플레이어만 (던지는 말 한 문장 + 이전 · 재생 · 다음). 소재와 이어가는 말은 소리로만.
 * 필터 칩은 없앴다(영상·글을 아래 줄로 떼어 내서 필요 없어짐).
 */
@Composable
fun MorningScreen(vm: MorningViewModel, parked: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (state.youtubeMissing) {
        AlertDialog(
            onDismissRequest = { vm.showYoutubeMissing(false) },
            title = { Text("유튜브 앱이 없어요", style = MaterialTheme.typography.headlineMedium) },
            text = { Text("영상은 유튜브 앱에서 열려요. 차량에 유튜브 앱을 설치한 뒤 다시 눌러 주세요.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { vm.showYoutubeMissing(false) }, modifier = Modifier.height(64.dp)) {
                    Text("확인", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
    // 열어 둔 글(읽어주기) 화면
    state.openTip?.let { tip ->
        ArticleReader(tip, state.readingParagraph, parked, onRead = { vm.readArticle(tip) }, onStop = { vm.stopReading() }, onClose = { vm.close() })
        return
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp)) {
        when {
            state.loading -> Loading("오늘의 아침 카드를 불러오고 있어요")
            state.cards.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(state.error ?: "오늘 아침 카드가 아직 없어요.", style = MaterialTheme.typography.titleLarge)
                BigButton("다시 불러오기") { vm.load() }
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                if (!parked || maxWidth < WIDE_MORNING) {
                    // 주행 중·좁은 창: 플레이어만
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Player(state, vm, large = true, showBranches = false)
                    }
                } else {
                    Feed(state, vm, onVideo = { id ->
                        vm.stopBriefing()
                        if (canOpenYoutube(context)) context.startActivity(youtubeIntent(id)) else vm.showYoutubeMissing(true)
                    })
                }
            }
        }
    }
}

/** 넓은 창과 좁은 창(상황판 옆)을 가르는 폭 */
private val WIDE_MORNING = 900.dp

@Composable
private fun Feed(state: MorningState, vm: MorningViewModel, onVideo: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 위 한 줄: 추천 듣기 · 전부 듣기 · 말투 세그먼트
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton(if (state.playing == PlayMode.Recommended) "■ 멈추기" else "▶ 추천 듣기 · 2장", accent = true) { vm.togglePlay(all = false) }
                PillButton(if (state.playing == PlayMode.All) "■ 멈추기" else "전부 듣기 · ${state.cards.size}장") { vm.togglePlay(all = true) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(420.dp)) {
                    Segmented(listOf(Tone.Coworker to "동료에게", Tone.Stranger to "처음 보는 사람"), state.tone) { vm.setTone(it) }
                }
            }
        }
        // 오늘의 한 장 (2칸): 평소엔 날씨, 브리핑 중이거나 카드를 고르면 그 카드의 플레이어
        item(span = { GridItemSpan(2) }) { Player(state, vm, large = false, showBranches = true) }
        itemsIndexed(state.cards, key = { _, c -> c.id }) { i, card ->
            if (i != state.current) {
                CardView(card, state.tone, expanded = card.id in state.expanded, onToggle = { vm.toggleExpanded(card.id) }, onSelect = { vm.select(i) })
            }
        }
        if (state.tips.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text("스몰토크 잘하는 법", style = MaterialTheme.typography.titleLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(state.tips, key = { it.id }) { tip ->
                            Box(Modifier.width(380.dp)) {
                                if (tip.isVideo) VideoTipCard(tip, parked = true) { tip.youtubeId?.let(onVideo) }
                                else ArticleTipCard(tip) { vm.open(tip) }
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
    }
}

/**
 * 플레이어 ("오늘의 한 장" 자리). 소재(작게) → 던지는 말(크게) → 상대 반응별 두 갈래(정차 중) → 이전 · 재생/멈춤 · 다음.
 * large: 주행 중·좁은 창. 던지는 말은 2줄까지 크게, 두 갈래는 쓰지 않는다(소리로 들려준다).
 */
@Composable
private fun Player(state: MorningState, vm: MorningViewModel, large: Boolean, showBranches: Boolean) {
    val card = state.currentCard ?: return
    val playing = state.playing != null
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.surface)
            .then(if (playing) Modifier.border(4.dp, Palette.accent, RoundedCornerShape(28.dp)) else Modifier)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.progress?.let { (n, total) -> "지금 듣는 카드 $n / $total" } ?: if (state.current == 0) "오늘의 한 장" else "고른 카드",
                style = MaterialTheme.typography.labelLarge, color = Palette.onAccent,
                modifier = Modifier.clip(CircleShape).background(Palette.accent).padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "${MORNING_CATEGORY_LABELS[card.category] ?: card.category} · ${card.shownHook}",
                style = MaterialTheme.typography.titleMedium, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            card.line(state.tone),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (large) 34.sp else 30.sp, lineHeight = if (large) 46.sp else 42.sp, fontWeight = FontWeight.Bold,
            ),
            maxLines = if (large) 3 else 4, overflow = TextOverflow.Ellipsis,
        )
        if (showBranches) Branches(card, expanded = true)
        if (large && !playing) {
            Text("운전 중에는 소리로 들려드려요. 이어가는 말까지 읽어 줘요.", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundControl("⏮", "이전 카드") { vm.step(-1) }
            Button(
                onClick = { vm.togglePlay(all = false) },
                modifier = Modifier.height(if (large) 96.dp else 80.dp).weight(1f),
                shape = RoundedCornerShape(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent),
            ) {
                Text(
                    if (playing) "■ 멈추기" else "▶ 추천 듣기 · 2장",
                    style = (if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.Bold),
                )
            }
            RoundControl("⏭", "다음 카드") { vm.step(1) }
        }
    }
}

/** 상대 반응별 두 갈래 (대본 형식: 이름 | 말). 점프 포인트 원칙을 그대로 담는다 */
@Composable
private fun Branches(card: MorningCard, expanded: Boolean) {
    if (!expanded) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BranchLine("짧게 받을 때", card.bridge)
        card.ifOpen?.let { BranchLine("얘기를 꺼낼 때", it) }
        card.sourceName?.let { Text("출처 $it", style = MaterialTheme.typography.bodyMedium, color = Palette.muted) }
    }
}

@Composable
private fun BranchLine(label: String, text: String) = Row {
    Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.muted, modifier = Modifier.width(130.dp).padding(top = 3.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
}

/** 목록 카드: 앞면은 소재(작게) + 던지는 말(크게)만. 누르면 플레이어로, "상대 반응별"은 펼치기 */
@Composable
private fun CardView(card: MorningCard, tone: Tone, expanded: Boolean, onToggle: () -> Unit, onSelect: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.surface).clickable(onClick = onSelect).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "${MORNING_CATEGORY_LABELS[card.category] ?: card.category} · ${card.shownHook}",
            style = MaterialTheme.typography.labelLarge, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(card.line(tone), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(
            if (expanded) "▴ 접기" else "▾ 상대 반응별 이어가기",
            style = MaterialTheme.typography.labelLarge, color = Palette.ink,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onToggle).padding(vertical = 10.dp),
        )
        Branches(card, expanded)
    }
}

@Composable
private fun PillButton(label: String, accent: Boolean = false, onClick: () -> Unit) = Button(
    onClick = onClick,
    modifier = Modifier.height(64.dp).widthIn(min = 200.dp),
    shape = RoundedCornerShape(32.dp),
    colors = if (accent) {
        ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent)
    } else {
        ButtonDefaults.buttonColors(containerColor = Palette.ink, contentColor = Palette.surface)
    },
) { Text(label, style = MaterialTheme.typography.labelLarge) }

/** 이전·다음 76dp 원 버튼 (차량 UX 최소 터치 영역) */
@Composable
private fun RoundControl(symbol: String, description: String, onClick: () -> Unit) = OutlinedButton(
    onClick = onClick,
    modifier = Modifier.size(76.dp).semantics { contentDescription = description },
    shape = CircleShape,
    contentPadding = PaddingValues(0.dp),
) {
    Text(symbol, fontSize = 26.sp, color = Palette.ink)
}
