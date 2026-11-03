package com.smalltoktok.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.smalltoktok.pleos.data.BuddyConfig
import com.smalltoktok.pleos.data.BuddyStyle
import com.smalltoktok.pleos.ui.theme.Palette

/**
 * 말동무 고정 카드 (2026-11-02): 연습이 아니라 심심할 때 수다 떠는 상대.
 * 만들기 전에는 회색 빈 카드("말동무 만들기"), 만든 뒤에는 고른 사진·스타일과 "수다 떨기"·"바꾸기".
 */
@Composable
internal fun BuddyCard(state: TalkState, vm: TalkViewModel, onSetup: () -> Unit) {
    val buddy = state.buddy
    if (buddy == null) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.ink.copy(alpha = 0.06f))
                .border(2.dp, Palette.muted.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .clickable(onClick = onSetup).padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Palette.muted.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Text("+", style = MaterialTheme.typography.displaySmall, color = Palette.muted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("말동무 만들기", style = MaterialTheme.typography.titleLarge, color = Palette.muted)
                Text("사진과 대화 스타일을 고르면, 심심할 때 수다 떨 상대가 생겨요", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.surface).padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AsyncImage(
            model = vm.api.characterImageUrl(buddy.characterId),
            contentDescription = "말동무 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp).clip(CircleShape).background(Palette.bg),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("말동무", style = MaterialTheme.typography.titleLarge)
            Text(
                "${buddy.style.label} · ${if (buddy.casual) "반말" else "존댓말"}",
                style = MaterialTheme.typography.bodyLarge, color = Palette.muted,
            )
        }
        OutlinedButton(onClick = onSetup, modifier = Modifier.height(64.dp)) {
            Text("바꾸기", style = MaterialTheme.typography.labelLarge)
        }
        Button(
            onClick = { vm.startBuddy() },
            modifier = Modifier.height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent),
        ) { Text("수다 떨기", style = MaterialTheme.typography.labelLarge) }
    }
}

/**
 * 주행 중 홈의 오른쪽 카드 (왼쪽은 연습 상대): 말동무 사진을 채우고 아래에 큰 "수다 떨기" 하나.
 * 연습 카드(DrivingHome)와 반씩 나눠 같은 크기로 보인다. 만들기·바꾸기는 정차 중에만.
 */
@Composable
internal fun BuddyDrivingCard(buddy: BuddyConfig, vm: TalkViewModel, modifier: Modifier) =
    Box(modifier.fillMaxHeight().padding(bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).clickable { vm.startBuddy() }) {
            AsyncImage(
                model = vm.api.characterImageUrl(buddy.characterId),
                contentDescription = "말동무 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(Palette.surface),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Palette.ink.copy(alpha = 0.55f), 0.22f to Color.Transparent,
                        0.65f to Color.Transparent, 1f to Palette.ink.copy(alpha = 0.9f),
                    ),
                ),
            )
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "말동무",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.onAccent,
                    modifier = Modifier.clip(CircleShape).background(Palette.accent).padding(horizontal = 18.dp, vertical = 10.dp),
                )
                Text(buddy.style.label, style = MaterialTheme.typography.headlineMedium, color = Palette.surface)
                Text("연습 말고 그냥 수다", style = MaterialTheme.typography.titleLarge, color = Palette.surface.copy(alpha = 0.85f))
            }
            Button(
                onClick = { vm.startBuddy() },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp).height(96.dp),
                shape = RoundedCornerShape(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent),
            ) { Text("수다 떨기", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) }
        }
    }

/**
 * 말동무 만들기·바꾸기 (정차 중에만): 성별 → 사진(인물 풀에서) → 대화 스타일 → 말투 → 저장.
 * 성별을 바꾸면 그 성별 사진만 보인다. 목소리는 고른 사진의 성별을 따른다.
 * adult: 성인만 고르는 스타일(설레는 썸 상대)을 보여 줄지. 아니면 숨기고 태어난 해 입력을 안내한다.
 */
@Composable
internal fun BuddySetupScreen(state: TalkState, vm: TalkViewModel, adult: Boolean, onDone: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadCharacters() }
    val current = state.buddy
    var gender by rememberSaveable { mutableStateOf(current?.gender ?: "f") }
    var characterId by rememberSaveable { mutableStateOf(current?.characterId) }
    var style by rememberSaveable {
        mutableStateOf(current?.style?.takeIf { adult || !it.adultOnly } ?: BuddyStyle.Chatty)
    }
    val styles = BuddyStyle.entries.filter { adult || !it.adultOnly }
    var casual by rememberSaveable { mutableStateOf(current?.casual ?: true) }
    val photos = state.characters.filter { it.gender == gender }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (current == null) "말동무 만들기" else "말동무 바꾸기", style = MaterialTheme.typography.headlineMedium)
                Text("연습이 아니라 그냥 수다예요. 끝나도 코칭이나 기록은 남지 않아요.", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
                Text("1. 성별", style = MaterialTheme.typography.titleLarge)
                Segmented(listOf("f" to "여자", "m" to "남자"), gender) {
                    gender = it
                    if (characterId?.startsWith(it) != true) characterId = null
                }
                Text("2. 사진", style = MaterialTheme.typography.titleLarge)
                if (photos.isEmpty()) Loading("사진을 불러오고 있어요")
            }
        }
        items(photos, key = { it.id }) { c ->
            val selected = c.id == characterId
            AsyncImage(
                model = vm.api.characterImageUrl(c.id),
                contentDescription = "인물 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp))
                    .background(Palette.surface)
                    .border(if (selected) 6.dp else 0.dp, if (selected) Palette.accent else Palette.bg, RoundedCornerShape(20.dp))
                    .clickable { characterId = c.id },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("3. 대화 스타일", style = MaterialTheme.typography.titleLarge)
                styles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { s -> StyleChip(s, s == style, Modifier.weight(1f)) { style = s } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (!adult) {
                    Text(
                        "설레는 썸 상대는 성인만 고를 수 있어요. 설정에서 태어난 해를 입력하면 보여요.",
                        style = MaterialTheme.typography.bodyLarge, color = Palette.muted,
                    )
                }
                Text("4. 말투", style = MaterialTheme.typography.titleLarge)
                Segmented(listOf(true to "반말", false to "존댓말"), casual) { casual = it }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDone, modifier = Modifier.height(72.dp)) {
                        Text("취소", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = {
                            characterId?.let { vm.saveBuddy(BuddyConfig(it, style, casual)) }
                            onDone()
                        },
                        enabled = characterId != null,
                        modifier = Modifier.widthIn(min = 240.dp).height(72.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent),
                    ) { Text(if (characterId == null) "사진을 골라 주세요" else "저장", style = MaterialTheme.typography.labelLarge) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StyleChip(style: BuddyStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) = Column(
    modifier.clip(RoundedCornerShape(20.dp))
        .background(if (selected) Palette.accent else Palette.surface)
        .clickable(onClick = onClick).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(if (selected) "✓ ${style.label}" else style.label, style = MaterialTheme.typography.titleLarge,
        color = if (selected) Palette.onAccent else Palette.ink)
    Text(style.description, style = MaterialTheme.typography.bodyLarge, color = if (selected) Palette.onAccent else Palette.muted)
}
