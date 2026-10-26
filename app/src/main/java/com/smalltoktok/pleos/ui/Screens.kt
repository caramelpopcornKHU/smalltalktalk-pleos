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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    val reflectVm: ReflectViewModel = viewModel()
    val morningVm: MorningViewModel = viewModel()
    val profileVm: ProfileViewModel = viewModel()
    val profile by profileVm.state.collectAsStateWithLifecycle()
    val habitVm: HabitViewModel = viewModel()
    // 다른 앱이 전체화면을 차지해 보이지 않게 되면 마이크·소리를 멈춘다 (나란히 띄운 창일 때는 계속)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.onBackground()
        reflectVm.onBackground()
        morningVm.stopBriefing()
        habitVm.stopSpeaking()
    }
    // 전체화면일 때 위 차량 정보 줄·아래 공조 바(시스템 바)와 겹치지 않게 안전 영역 안에만 그린다.
    // 상황판 옆 창(일부 화면)일 때는 겹치는 영역이 없어 여백이 0이 된다
    Box(Modifier.fillMaxSize().background(Palette.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        when {
            !state.booted || !profile.loaded -> SplashScreen()
            // 처음 켰을 때 성·이름 입력 (나중에 할게요로 넘길 수 있다)
            profile.needsName -> NameScreen(profileVm, parked)
            state.phase == Phase.Idle -> Column(Modifier.fillMaxSize()) {
                CapsuleHeader(
                    unread = false,
                    onBell = null,
                    onGear = { extra = Extra.Settings },
                    initial = profile.initial,
                )
                Box(Modifier.weight(1f)) {
                    when (extra) {
                        Extra.Settings -> NameScreen(profileVm, parked, asSettings = true) { extra = null }
                        null -> when (tab) {
                            Tab.Evening -> HomeScreen(state, parked, vm)
                            Tab.Reflect -> ReflectScreen(reflectVm, parked) { vm.startNew(it) }
                            Tab.Morning -> MorningScreen(morningVm, parked)
                            Tab.Habits -> HabitScreen(habitVm, parked, profile.given) { distance -> vm.startForDistance(distance) }
                        }
                    }
                }
                FloatingTabBar(current = tab.takeIf { extra == null }) {
                    tab = it
                    extra = null
                }
            }
            state.phase == Phase.Done -> CoachingScreen(state.feedback, parked, vm)
            state.phase == Phase.Error -> ErrorScreen(state.error, vm)
            else -> TalkScreen(state, parked, vm)
        }
    }
}

/** 앱을 켤 때: 로고와 함께 첫 불러오기(익명 로그인, 상황 목록)를 기다린다 */
@Composable
private fun SplashScreen() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val pulse = rememberInfiniteTransition(label = "splash")
        val s by pulse.animateFloat(1f, 1.06f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "s")
        Box(Modifier.scale(s)) { RallyMark(size = 140.dp) }
        Text("스몰톡톡", style = MaterialTheme.typography.displaySmall)
        Text("오늘의 대화 상대를 부르고 있어요", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
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
            // 주행 중: 오늘의 상대 사진과 큰 시작 버튼만 (목록을 읽고 고르는 일은 정차 중에)
            !parked -> state.today?.let { DrivingHome(it, state, vm, Modifier.weight(1f)) }
            else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // 2026-10-23 시안(docs/prototypes/today-partner.html): 넓은 창은 왼쪽 큰 세로 카드(바로 시작하기·다른 사람)
                // + 오른쪽 사진 카드 목록, 좁은 창은 A안(세로 카드 위, 목록 아래). 사진을 세로 그대로 써서 얼굴이 잘리지 않게.
                // 처음에 넣은 흐린 후보 카드 둘은 써 보고 뺐다(같은 날 피드백)
                if (maxWidth > WIDE_HOME) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        state.today?.let { t ->
                            TodayPartner(t, parked = true, state, vm, Modifier.fillMaxHeight().padding(bottom = 8.dp).aspectRatio(3f / 4f))
                        }
                        ScenarioGrid(state, vm, Modifier.weight(1f).fillMaxHeight(), today = null)
                    }
                } else {
                    ScenarioGrid(state, vm, Modifier.fillMaxSize(), today = state.today)
                }
            }
        }
    }
}

/** 넓은 창(전체화면 등)과 좁은 창(상황판 옆 1칸)을 가르는 폭 */
private val WIDE_HOME = 900.dp

/** 상황 목록. 좁은 창에서는 맨 위에 오늘의 상대 세로 카드(A안)를 함께 넣는다 */
@Composable
private fun ScenarioGrid(state: TalkState, vm: TalkViewModel, modifier: Modifier, today: Scenario?) = LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 220.dp),
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    today?.let { t ->
        item(key = "today", span = { GridItemSpan(maxLineSpan) }) {
            TodayPartner(t, parked = true, state, vm, Modifier.fillMaxWidth().aspectRatio(3f / 3.2f))
        }
    }
    item(span = { GridItemSpan(maxLineSpan) }) { Section("누구와, 어떤 자리에서?") }
    items(state.shared, key = { it.id }) { ScenarioCard(it, state.rallies[it.id], it.casual(state.casual), vm) }
    if (state.mine.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { Section("내 이야기로 만든 연습") }
        items(state.mine, key = { it.id }) { ScenarioCard(it, state.rallies[it.id], it.casual(state.casual), vm) }
    }
    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
}


/**
 * 오늘의 상대 (폰 앱 2026-10-22): 큰 사진 + 상황·첫 마디 미리 보기 + 바로 시작하기 / 다른 사람.
 * 사진 어디를 눌러도 시작한다. 첫 마디 글은 정차 중에만 보여준다.
 * 시작 버튼 바로 위 존댓말 | 반말 스위치(2026-10-26 UX 검토): 말투는 "어떻게 시작할지"라 시작 버튼 옆에 둔다.
 * 처음엔 작은 칩(카드 오른쪽 위)이었는데 있는지도 모르겠다는 피드백. 바꾸면 첫 마디 미리 보기도 바로 바뀐다.
 */
@Composable
private fun TodayPartner(s: Scenario, parked: Boolean, state: TalkState, vm: TalkViewModel, modifier: Modifier) {
    val casual = s.casual(state.casual)
    Box(modifier.clip(RoundedCornerShape(28.dp)).clickable { vm.start(s) }) {
        Portrait(s, vm, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.4f to Color.Transparent, 1f to Palette.ink.copy(alpha = 0.85f)),
            ),
        )
        Text(
            "오늘의 상대",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.onAccent,
            modifier = Modifier.padding(24.dp).clip(CircleShape).background(Palette.accent).padding(horizontal = 18.dp, vertical = 10.dp),
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${s.whoShort} · ${s.who}", style = MaterialTheme.typography.bodyLarge, color = Palette.surface)
            Text(s.title, style = MaterialTheme.typography.headlineMedium, color = Palette.surface)
            if (parked) {
                s.firstSay(casual)?.let {
                    Text("“$it”", style = MaterialTheme.typography.bodyLarge, color = Palette.surface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!state.speechHintSeen) {
                Text(
                    if (s.mine) "상: 말수 적은 상대예요" else "반말: 말 놓은 또래처럼 · 상: 말수 적은 상대",
                    style = MaterialTheme.typography.bodyLarge, color = Palette.surface.copy(alpha = 0.85f),
                )
            }
            // 내 이야기로 만든 연습은 말투가 정해져 있어 말투 스위치는 공유 상황에만
            if (!s.mine) {
                Segmented(listOf(false to "존댓말", true to "반말"), casual) { vm.setCasual(it, announce = !parked) }
            }
            // 상대 난이도 (2026-10-26): 지금까지 상대가 너무 잘 이끌어 줘서 하·중·상을 고른다
            Segmented(Difficulty.entries.map { it to "난이도 ${it.label}" }, state.difficulty) { vm.setDifficulty(it, announce = !parked) }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.start(s) },
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent),
                ) { Text("바로 시작하기", style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = { vm.shuffleToday() },
                    modifier = Modifier.height(80.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.surface),
                ) { Text("다른 사람", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/**
 * 주행 중 홈 (2026-10-26 피드백 두 번): 가로로 꽉 찬 사진은 얼굴이 반쯤 잘렸고, 카드 옆에 스위치를 따로 두니 분리돼 보였다.
 * 세로에 가까운 카드 하나에 사진을 채우고, 이름표는 위로, 큰 스위치(88dp)와 시작 버튼(96dp)은 아래(얼굴 아래 상반신 위)에 넣는다.
 * 카드는 높이에 맞추고 가운데 둔다. 폭이 높이의 0.85배라 사진(3:4)은 위아래만 조금 잘리고 얼굴은 그대로다.
 */
@Composable
private fun DrivingHome(s: Scenario, state: TalkState, vm: TalkViewModel, modifier: Modifier) =
    Box(modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
        val casual = s.casual(state.casual)
        Box(
            Modifier.fillMaxHeight().aspectRatio(0.85f, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(28.dp)).clickable { vm.start(s) },
        ) {
            Portrait(s, vm, Modifier.fillMaxSize())
            // 위는 이름표가 읽히게 살짝, 아래는 스위치가 읽히게 진하게. 가운데(얼굴)는 비워 둔다
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Palette.ink.copy(alpha = 0.55f), 0.22f to Color.Transparent,
                        0.55f to Color.Transparent, 0.75f to Palette.ink.copy(alpha = 0.85f), 1f to Palette.ink.copy(alpha = 0.95f),
                    ),
                ),
            )
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "오늘의 상대",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.onAccent,
                    modifier = Modifier.clip(CircleShape).background(Palette.accent).padding(horizontal = 18.dp, vertical = 10.dp),
                )
                Text(s.whoShort, style = MaterialTheme.typography.titleLarge, color = Palette.accent)
                Text(s.title, style = MaterialTheme.typography.headlineMedium, color = Palette.surface)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!s.mine) Segmented(listOf(false to "존댓말", true to "반말"), casual, large = true) { vm.setCasual(it, announce = true) }
                Segmented(Difficulty.entries.map { it to "난이도 ${it.label}" }, state.difficulty, large = true) {
                    vm.setDifficulty(it, announce = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { vm.start(s) },
                        modifier = Modifier.weight(1f).height(96.dp),
                        shape = RoundedCornerShape(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent),
                    ) { Text("바로 시작하기", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) }
                    OutlinedButton(
                        onClick = { vm.shuffleToday() },
                        modifier = Modifier.height(96.dp),
                        shape = RoundedCornerShape(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.surface),
                    ) { Text("다른 사람", style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
    }

/**
 * 존댓말 | 반말, 난이도 하 | 중 | 상 칸 스위치. 늘 하나가 선택돼 있어 지금 상태가 보인다.
 * 라임은 "바로 시작하기"에만 쓰고, 선택된 칸은 흰 바탕 + 먹색 글자(주 버튼이 둘처럼 보이지 않게).
 * 칸을 누르면 그 칸이 탭을 받으므로 카드 전체의 "눌러서 시작"이 같이 실행되지 않는다.
 * 두 줄이 쌓여도 카드 아래가 너무 높아지지 않게 56dp (UX 검토의 좁은 창 높이).
 * large: 주행 중 화면용 88dp (흔들리는 차 안에서도 한 번에 누르게, Pleos 가이드 72~88dp)
 */
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, current: T, large: Boolean = false, onChange: (T) -> Unit) {
    val height = if (large) 88.dp else 56.dp
    Row(
        Modifier.widthIn(max = if (large) 720.dp else 420.dp).fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2))
            .background(Palette.ink.copy(alpha = if (large) 0.7f else 0.55f)).padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val selected = current == value
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(height / 2))
                    .background(if (selected) Palette.surface else Color.Transparent)
                    .clickable { onChange(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (selected) "✓ $label" else label,
                    style = (if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
                        .copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) Palette.ink else Palette.surface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** "반말"·"난이도 상" 같은 작은 표시 (테두리). 상황 카드와 대화 화면에서 쓴다 */
@Composable
private fun SpeechTag(text: String = "반말", onDark: Boolean = false) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = if (onDark) Palette.surface else Palette.ink,
    modifier = Modifier.border(2.dp, if (onDark) Palette.surface else Palette.ink, CircleShape)
        .padding(horizontal = 12.dp, vertical = 4.dp),
)

@Composable
private fun Section(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun ScenarioCard(s: Scenario, rally: Int?, casual: Boolean, vm: TalkViewModel) {
    Column(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.surface)
            .clickable { vm.start(s) },
    ) {
        Portrait(s, vm, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${s.whoShort} · ${s.place}", style = MaterialTheme.typography.bodyLarge, color = Palette.muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                // 반말로 시작하는 상황만 표시 (존댓말은 기본이라 표시하지 않는다)
                if (casual) {
                    Spacer(Modifier.width(8.dp))
                    SpeechTag()
                }
            }
            // 해본 상황은 최고 랠리 수를 보여준다
            rally?.let { Text("최고 랠리 ${it}번", style = MaterialTheme.typography.labelLarge, color = Palette.onAccent) }
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
        // 주행 중에는 사진이 시선을 끌지 않게 어둡게 (이미지는 필요한 만큼만)
        if (!parked) Box(Modifier.fillMaxSize().background(Palette.ink.copy(alpha = 0.55f)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Palette.ink.copy(alpha = 0.85f)),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.whoShort, style = MaterialTheme.typography.titleLarge, color = Palette.accent)
                // 반말이면 표시, 난이도는 늘 표시. 누를 수 없고 대화 중에는 바꾸지 않는다
                if (state.talkCasual) {
                    Spacer(Modifier.width(12.dp))
                    SpeechTag("반말", onDark = true)
                }
                Spacer(Modifier.width(8.dp))
                SpeechTag("난이도 ${state.talkDifficulty.label}", onDark = true)
            }
            val lastPartner = state.messages.lastOrNull { it.speaker == "partner" }?.text
            when (state.phase) {
                Phase.Starting -> Caption("대화를 준비하고 있어요")
                Phase.PartnerSpeaking ->
                    if (parked && lastPartner != null) Caption(lastPartner) else Caption("${s.whoShort}이(가) 말하고 있어요")
                Phase.Listening -> Row(verticalAlignment = Alignment.CenterVertically) {
                    RallyBall(listening = true, onClick = { vm.voice.stopListening() }, animate = parked)
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
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 정차 중 옆 창에서 읽는 대본이라 본문(22sp)보다 크게 둔다 (2026-10-23 피드백)
        state.messages.forEach { m ->
            Row {
                Text(
                    if (m.speaker == "partner") who else "나",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 26.sp),
                    color = if (m.speaker == "partner") Palette.muted else Palette.ink,
                    modifier = Modifier.width(140.dp),
                )
                Text(
                    m.text.ifEmpty { "(말하지 못했어요)" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp, lineHeight = 40.sp),
                )
            }
        }
    }
}

/** 라임 공 마이크. 듣는 동안 숨 쉬듯 커졌다 작아지고, 누르면 말 끝내기 */
@Composable
internal fun RallyBall(listening: Boolean, onClick: () -> Unit, animate: Boolean = true) {
    // 주행 중에는 반복 애니메이션을 쓰지 않고(Pleos 주행 안전 가이드) 듣는 중임을 먹색 테두리로만 보여준다
    val pulse = rememberInfiniteTransition(label = "ball")
    val s by pulse.animateFloat(1f, if (listening && animate) 1.12f else 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Box(
        Modifier.size(88.dp).scale(s).clip(CircleShape)
            .then(if (listening && !animate) Modifier.border(6.dp, Palette.ink, CircleShape) else Modifier)
            .background(Palette.accent).clickable(onClick = onClick),
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
            // 한 마디 얹어볼 순간을 맨 위 하이라이트로 (2026-10-23 피드백): 다음 대화에서 바로 써먹을 한마디가 핵심이라서
            feedback.missedMoment?.let { MissedMoment(it, feedback.suggestion, vm) }
            feedback.highlight?.let { CoachCard("좋았던 한마디", "“$it”", feedback.highlightReason) }
            feedback.deliveryFeedback?.let { CoachCard("말하기 흐름", it, null) }
            if (feedback.point.isNotEmpty()) CoachCard("관계 포인트", feedback.point, null)
        }
        BigButton("처음으로") { vm.backHome() }
    }
}

/** 라임 하이라이트 카드: 그 순간 + "이렇게 이어가면 더 자연스러워요" 한마디 + 들어보기 */
@Composable
private fun MissedMoment(moment: String, suggestion: String?, vm: TalkViewModel) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.accent).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("한 마디 얹어볼 순간", style = MaterialTheme.typography.labelLarge, color = Palette.onAccent)
        Text(moment, style = MaterialTheme.typography.titleLarge, color = Palette.onAccent)
        suggestion?.let { line ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.surface).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("이렇게 이어가면 대화가 더 자연스러워요", style = MaterialTheme.typography.labelLarge, color = Palette.muted)
                Text("“$line”", style = MaterialTheme.typography.headlineMedium, color = Palette.ink)
                Text(
                    "맞장구 뒤에 내 생각이나 질문을 한 조각 얹으면 상대가 이어 말하기 쉬워져요.",
                    style = MaterialTheme.typography.bodyLarge, color = Palette.muted,
                )
                OutlinedButton(onClick = { vm.say(line) }, modifier = Modifier.height(64.dp)) {
                    Text("들어보기", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
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
internal fun Loading(text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    CircularProgressIndicator(color = Palette.ink)
    Spacer(Modifier.width(16.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

/** 차량 터치 영역은 크게 (높이 88dp) */
@Composable
internal fun BigButton(text: String, accent: Boolean = false, onClick: () -> Unit) = Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(88.dp),
    shape = RoundedCornerShape(44.dp),
    colors = if (accent) {
        ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent)
    } else {
        ButtonDefaults.buttonColors(containerColor = Palette.ink, contentColor = Palette.surface)
    },
) { Text(text, style = MaterialTheme.typography.labelLarge) }
