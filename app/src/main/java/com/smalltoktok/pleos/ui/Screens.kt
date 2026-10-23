package com.smalltoktok.pleos.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.smalltoktok.pleos.data.Feedback
import com.smalltoktok.pleos.data.Scenario
import com.smalltoktok.pleos.ui.theme.Palette

/**
 * 앱 진입. 탭 화면은 헤더 캡슐 + 하단 탭 바 사이에 두고,
 * 대화·코칭은 상대에게 집중하도록 화면 전체를 쓴다(폰 앱과 같다).
 * 탭 선택은 rememberSaveable이라 창 크기가 바뀌거나 앱이 다시 만들어져도 유지된다.
 */
@Composable
fun App(vm: TalkViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val parked by vm.driving.parked.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Evening) }
    var extra by rememberSaveable { mutableStateOf<Extra?>(null) }
    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        when (state.phase) {
            Phase.Idle -> Column(Modifier.fillMaxSize()) {
                CapsuleHeader(
                    unread = false,
                    onBell = { extra = Extra.Notifications },
                    onGear = { extra = Extra.Settings },
                )
                Box(Modifier.weight(1f)) {
                    when (extra) {
                        Extra.Notifications -> ComingSoon("알림", "아침 카드와 새 상대 소식이 여기에 모여요.")
                        Extra.Settings -> ComingSoon("설정", "알림 시각과 계정 설정을 준비하고 있어요.")
                        null -> when (tab) {
                            Tab.Evening -> HomeScreen(state, parked, vm)
                            Tab.Reflect -> ComingSoon("아쉬운 대화", "아쉬웠던 순간을 말로 털어놓고 연습 상황으로 만들어요.")
                            Tab.Morning -> ComingSoon("아침 카드", "출근길에 오늘 던질 한마디를 들려드려요.")
                            Tab.Notes -> ComingSoon("관계 노트", "연습하며 쌓인 관계 포인트를 모아 봐요.")
                        }
                    }
                }
                FloatingTabBar(current = tab.takeIf { extra == null }) {
                    tab = it
                    extra = null
                }
            }
            Phase.Done -> CoachingScreen(state.feedback, parked, vm)
            Phase.Error -> ErrorScreen(state.error, vm)
            else -> TalkScreen(state, parked, vm)
        }
    }
}

// ---------- 홈 ----------

/**
 * 주행 중: 큰 버튼 하나("아무 상대와 시작")만. 목록을 읽고 고르는 일은 정차 중에만.
 * 정차 중: 상황 카드 목록.
 */
@Composable
private fun HomeScreen(state: TalkState, parked: Boolean, vm: TalkViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        when {
            state.loadingScenarios -> Loading("상대를 불러오고 있어요")
            state.scenarios.isEmpty() -> {
                Text(state.error ?: "연습할 상황이 없어요.", style = MaterialTheme.typography.bodyLarge)
                BigButton("다시 불러오기") { vm.loadScenarios() }
            }
            !parked -> {
                Text("운전 중에는 목소리로만 대화해요.", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
                BigButton("아무 상대와 대화 시작", accent = true) { vm.start(state.scenarios.random()) }
            }
            else -> {
                Text("오늘은 누구와 이야기해 볼까요?", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(state.scenarios, key = { it.id }) { ScenarioCard(it, vm) }
                }
            }
        }
    }
}

@Composable
private fun ScenarioCard(s: Scenario, vm: TalkViewModel) {
    Column(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.surface)
            .clickable { vm.start(s) },
    ) {
        Portrait(s, vm, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${s.whoShort} · ${s.place}", style = MaterialTheme.typography.bodyLarge, color = Palette.muted, maxLines = 1)
        }
    }
}

// ---------- 대화 ----------

/**
 * 상대 사진을 크게, 그 위에 지금 단계만 크게 보여준다.
 * 자막(상대 말 글자)은 정차 중에만 보여준다. 주행 중에는 귀로 듣고 말로 답한다.
 * 창이 넓으면(가로 2칸) 사진과 대본을 나란히, 좁으면 사진 위주로.
 */
@Composable
private fun TalkScreen(state: TalkState, parked: Boolean, vm: TalkViewModel) {
    val partial by vm.voice.partial.collectAsStateWithLifecycle()
    val scenario = state.scenario ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight * 1.2f
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                PartnerStage(scenario, state, partial, parked, vm, Modifier.weight(1f).fillMaxHeight())
                if (parked) Script(state, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            PartnerStage(scenario, state, partial, parked, vm, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PartnerStage(
    s: Scenario,
    state: TalkState,
    partial: String,
    parked: Boolean,
    vm: TalkViewModel,
    modifier: Modifier,
) {
    Box(modifier) {
        Portrait(s, vm, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Palette.ink.copy(alpha = 0.85f)),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(s.whoShort, style = MaterialTheme.typography.titleLarge, color = Palette.accent)
            val lastPartner = state.messages.lastOrNull { it.speaker == "partner" }?.text
            when (state.phase) {
                Phase.Starting -> Caption("대화를 준비하고 있어요")
                Phase.PartnerSpeaking ->
                    if (parked && lastPartner != null) Caption(lastPartner) else Caption("${s.whoShort}이(가) 말하고 있어요")
                Phase.Listening -> Row(verticalAlignment = Alignment.CenterVertically) {
                    RallyBall(listening = true, onClick = { vm.voice.stopListening() })
                    Spacer(Modifier.width(20.dp))
                    Caption(partial.ifEmpty { "말씀하세요" })
                }
                Phase.Thinking -> Caption("…")
                Phase.Finishing -> Caption("오늘 대화를 돌아보고 있어요")
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "랠리 ${state.partnerTurns} / ${state.maxPartnerTurns}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.surface,
                )
                Spacer(Modifier.weight(1f))
                if (state.phase != Phase.Finishing) {
                    OutlinedButton(
                        onClick = { vm.endNow() },
                        modifier = Modifier.height(72.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.surface),
                    ) { Text("대화 끝내기", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

/** 말풍선 대신 대본 형식 (이름 | 말). 정차 중 넓은 창에서만 */
@Composable
private fun Script(state: TalkState, modifier: Modifier) {
    val who = state.scenario?.whoShort ?: "상대"
    Column(
        modifier.background(Palette.surface).padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.messages.forEach { m ->
            Row {
                Text(
                    if (m.speaker == "partner") who else "나",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (m.speaker == "partner") Palette.muted else Palette.ink,
                    modifier = Modifier.width(120.dp),
                )
                Text(m.text.ifEmpty { "(말하지 못했어요)" }, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** 라임 공 마이크. 듣는 동안 숨 쉬듯 커졌다 작아지고, 누르면 말 끝내기 */
@Composable
private fun RallyBall(listening: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "ball")
    val s by pulse.animateFloat(1f, if (listening) 1.12f else 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Box(
        Modifier.size(88.dp).scale(s).clip(CircleShape).background(Palette.accent).clickable(onClick = onClick),
    )
}

// ---------- 코칭 ----------

/** 글이 많아 정차 중에만 연다. 주행 중이면 "정차하면 볼 수 있어요" */
@Composable
private fun CoachingScreen(feedback: Feedback?, parked: Boolean, vm: TalkViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("오늘의 랠리", style = MaterialTheme.typography.headlineMedium)
        if (!parked) {
            Text("차를 세우면 코칭을 볼 수 있어요.", style = MaterialTheme.typography.titleLarge)
            Text("운전 중에는 화면을 보지 않아도 돼요. 코칭은 저장해 둘게요.", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
        } else if (feedback != null) {
            feedback.highlight?.let { CoachCard("좋았던 한마디", "“$it”", feedback.highlightReason) }
            feedback.missedMoment?.let { CoachCard("한 마디 얹어볼 순간", it, feedback.suggestion?.let { s -> "이렇게 말해볼 수도 있어요: “$s”" }) }
            feedback.deliveryFeedback?.let { CoachCard("말하기 흐름", it, null) }
            if (feedback.point.isNotEmpty()) CoachCard("관계 포인트", feedback.point, null)
        }
        BigButton("처음으로") { vm.backHome() }
    }
}

@Composable
private fun CoachCard(label: String, body: String, sub: String?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.surface).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.muted)
        Text(body, style = MaterialTheme.typography.titleLarge)
        sub?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.muted) }
    }
}

// ---------- 공통 ----------

@Composable
private fun ErrorScreen(message: String?, vm: TalkViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message ?: "문제가 생겼어요.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        BigButton("처음으로") { vm.backHome() }
    }
}

@Composable
private fun Portrait(s: Scenario, vm: TalkViewModel, modifier: Modifier) {
    if (s.characterId != null) {
        AsyncImage(
            model = vm.api.characterImageUrl(s.characterId),
            contentDescription = s.who,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(Palette.ink),
        )
    } else {
        Box(modifier.background(Palette.ink))
    }
}

@Composable
private fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.displaySmall, color = Palette.surface, maxLines = 4, overflow = TextOverflow.Ellipsis)

@Composable
private fun Loading(text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    CircularProgressIndicator(color = Palette.ink)
    Spacer(Modifier.width(16.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

/** 차량 터치 영역은 크게 (높이 88dp) */
@Composable
private fun BigButton(text: String, accent: Boolean = false, onClick: () -> Unit) = Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(88.dp),
    shape = RoundedCornerShape(44.dp),
    colors = if (accent) {
        ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent)
    } else {
        ButtonDefaults.buttonColors(containerColor = Palette.ink, contentColor = Palette.surface)
    },
) { Text(text, style = MaterialTheme.typography.labelLarge) }
