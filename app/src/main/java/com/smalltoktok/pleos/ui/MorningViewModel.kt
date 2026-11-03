package com.smalltoktok.pleos.ui

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smalltoktok.pleos.Services
import com.smalltoktok.pleos.data.MorningCard
import com.smalltoktok.pleos.data.Tip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Tone { Coworker, Stranger }

/** 지금 무엇을 읽고 있나: 추천 브리핑(2장 + 맺음), 전부 듣기, 한 장만 */
enum class PlayMode { Recommended, All, Single }

data class MorningState(
    val loading: Boolean = true,
    val cards: List<MorningCard> = emptyList(),
    val tone: Tone = Tone.Coworker,
    /** 플레이어에 올라온 카드 (cards 안 번호). 처음엔 날씨(맨 앞) */
    val current: Int = 0,
    /** 읽는 중이면 그 방식 */
    val playing: PlayMode? = null,
    /** 브리핑 진행 (지금 몇 번째 / 전체) */
    val progress: Pair<Int, Int>? = null,
    /** 상대 반응별 두 갈래를 펼친 카드 id */
    val expanded: Set<String> = emptySet(),
    val error: String? = null,
    /** 스몰토크 잘하는 법 영상·글 (2026-10-23). 2026-10-26부터 소재 카드 사이에 섞지 않고 아래 한 줄로 */
    val tips: List<Tip> = emptyList(),
    /** 영상 카드를 눌렀는데 유튜브 앱이 없을 때 설치 안내를 띄운다 (Pleos에는 기본으로 없다) */
    val youtubeMissing: Boolean = false,
    /** 열어 둔 글(읽어주기) */
    val openTip: Tip? = null,
    /** 글을 읽는 중이면 지금 읽는 문단 번호 */
    val readingParagraph: Int? = null,
) {
    val currentCard get() = cards.getOrNull(current)
}

/** 글 본문을 읽을 문단으로 나눈다 (빈 줄 기준) */
fun Tip.paragraphs(): List<String> = body.orEmpty().split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }

/** 폰 앱 카테고리 이름 (packages/shared/src/morning.ts MORNING_CATEGORIES) */
val MORNING_CATEGORY_LABELS = mapOf(
    "weather" to "날씨",
    "fun-fact" to "그거 아세요?",
    "season" to "계절",
    "food" to "점심·제철 음식",
    "outing" to "나들이·축제",
    "culture" to "전시·공연",
    "fuel" to "기름값",
    "movie-drama" to "영화·드라마",
    "kpop" to "K-pop",
    "issue" to "어제의 이슈",
    "sports" to "스포츠",
    "ott" to "드라마·예능·OTT",
)

fun MorningCard.line(tone: Tone) = if (tone == Tone.Coworker) coworkerLine else strangerLine

/**
 * 아침 카드 (2026-10-26 전문가 3명 합의안).
 * 카드 = 소재 명사구(hook) + 던지는 말 + 상대 반응별 두 갈래(짧게 받을 때 내 디테일 / 얘기를 꺼낼 때 점프 포인트 질문).
 * 브리핑 대본(음성 UX 합의):
 *   추천: 시작 인사 → 카드 2장 [신호음] "오늘은 {hook} 얘기예요. 이렇게 던져 보세요(두 번째부터 '이번엔 이렇게요'). {던지는 말}"
 *         → 1.2초 쉼(속으로 따라 말할 시간) → "대답이 짧으면, {내 디테일}" → 맺음에서 첫 카드 말을 한 번 더 → 멈춤
 *   전부 듣기: 시작 한 줄 → 카드마다 소재와 던지는 말까지만(이어가는 말 없이), 맺음 없음
 *   한 장만(이전·다음 버튼): 소재 → 던지는 말 → 쉼 → 대답이 짧으면 → 얘기를 꺼내면, {점프 포인트 질문}
 * 대사가 앱이 나에게 묻는 말로 들리지 않게 "이렇게 던져 보세요"를 꼭 붙인다.
 */
class MorningViewModel(app: Application) : AndroidViewModel(app) {
    private val services = Services.get(app)
    private val prefs = app.getSharedPreferences("morning", 0)

    private val _state = MutableStateFlow(
        MorningState(tone = if (prefs.getString("tone", null) == "stranger") Tone.Stranger else Tone.Coworker),
    )
    val state: StateFlow<MorningState> = _state
    private var brief: Job? = null
    /** 다음 카드 신호음. 만들 수 없는 기기면 신호음 없이 읽는다 */
    private val chime = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()

    init {
        load()
    }

    override fun onCleared() {
        chime?.release()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { services.api.morningCards() }
                .onSuccess { cards -> _state.update { it.copy(loading = false, cards = cards, current = 0) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
        // 영상·글은 실패해도 아침 카드는 그대로 보여준다
        viewModelScope.launch {
            runCatching { services.api.tips() }.onSuccess { tips -> _state.update { it.copy(tips = tips) } }
        }
    }

    fun toggleExpanded(id: String) = _state.update {
        it.copy(expanded = if (id in it.expanded) it.expanded - id else it.expanded + id)
    }

    /** 목록의 카드를 누르면 플레이어(오늘의 한 장 자리)로 올린다 */
    fun select(index: Int) {
        stopBriefing()
        _state.update { it.copy(current = index) }
    }

    /** 글을 열자마자 읽기 시작 (영상은 화면에서 유튜브 앱으로 바로 연다) */
    fun open(tip: Tip) {
        stopBriefing()
        _state.update { it.copy(openTip = tip, readingParagraph = null) }
        readArticle(tip)
    }

    fun showYoutubeMissing(show: Boolean) = _state.update { it.copy(youtubeMissing = show) }

    fun close() {
        brief?.cancel()
        services.voice.stopSpeaking()
        _state.update { it.copy(openTip = null, readingParagraph = null) }
    }

    /** 글을 문단마다 읽는다. 지금 읽는 문단을 화면에 표시한다 */
    fun readArticle(tip: Tip) {
        brief?.cancel()
        services.voice.stopSpeaking()
        brief = viewModelScope.launch {
            try {
                services.voice.speak(tip.title)
                tip.paragraphs().forEachIndexed { i, p ->
                    _state.update { it.copy(readingParagraph = i) }
                    services.voice.speak(p)
                }
            } finally {
                _state.update { it.copy(readingParagraph = null) }
            }
        }
    }

    fun stopReading() {
        brief?.cancel()
        services.voice.stopSpeaking()
        _state.update { it.copy(readingParagraph = null) }
    }

    fun setTone(tone: Tone) {
        prefs.edit().putString("tone", if (tone == Tone.Stranger) "stranger" else "coworker").apply()
        _state.update { it.copy(tone = tone) }
    }

    /** 추천 2장: 날씨는 늘 첫 장, 나머지 한 장은 그날 날짜로 섞어 고른다(같은 날에는 같은 추천) */
    fun recommended(cards: List<MorningCard>): List<MorningCard> {
        val (weather, others) = cards.partition { it.category == "weather" }
        val seed = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toEpochDay()
        return (weather.take(1) + others.shuffled(java.util.Random(seed))).take(RECOMMENDED)
    }

    /** 재생 버튼: 읽는 중이면 멈추고, 아니면 추천 브리핑(또는 전부 듣기) */
    fun togglePlay(all: Boolean = false) {
        if (brief?.isActive == true) return stopBriefing()
        val cards = _state.value.cards
        if (cards.isEmpty()) return
        val playlist = (if (all) cards else recommended(cards)).map { cards.indexOf(it) }
        brief = viewModelScope.launch { runBriefing(playlist, if (all) PlayMode.All else PlayMode.Recommended) }
    }

    /** 이전·다음: 브리핑 중이면 그 카드로 건너뛰고, 아니면 그 카드 한 장을 읽는다 */
    fun step(delta: Int) {
        val st = _state.value
        if (st.cards.isEmpty()) return
        val target = (st.current + delta).mod(st.cards.size)
        brief?.cancel()
        services.voice.stopSpeaking()
        _state.update { it.copy(current = target) }
        brief = viewModelScope.launch { runBriefing(listOf(target), PlayMode.Single) }
    }

    private suspend fun runBriefing(playlist: List<Int>, mode: PlayMode) {
        val cards = _state.value.cards
        try {
            _state.update { it.copy(playing = mode) }
            when (mode) {
                PlayMode.Recommended -> say("좋은 아침이에요. 오늘 써먹을 한마디, ${playlist.size}개 골라 왔어요.")
                PlayMode.All -> say("오늘 카드 ${playlist.size}장, 차례로 들려드릴게요.")
                PlayMode.Single -> Unit
            }
            playlist.forEachIndexed { n, i ->
                val c = cards[i]
                _state.update { it.copy(current = i, progress = if (mode == PlayMode.Single) null else (n + 1) to playlist.size) }
                beep()
                // 그거 아세요?는 지식 → 왜 → 이럴 때 → 그 상황에 걸어 꺼내는 말 순서로 (2026-11-02)
                if (c.isFact) {
                    say("오늘의 신기한 지식이에요. ${c.topic}.")
                    if (mode != PlayMode.All) c.why?.let { say(it) }
                    delay(PAUSE_MS)
                    say("${c.whenToUse}, 이렇게 꺼내 보세요.")
                    say(c.line(_state.value.tone))
                    return@forEachIndexed
                }
                val lead = if (n == 0) "이렇게 던져 보세요." else "이번엔 이렇게요."
                say("오늘은 ${c.shownHook} 얘기예요. $lead")
                say(c.line(_state.value.tone))
                if (mode == PlayMode.All) return@forEachIndexed
                delay(PAUSE_MS)
                say("대답이 짧으면, ${c.bridge}")
                if (mode == PlayMode.Single) c.ifOpen?.let { say("얘기를 꺼내면, 이렇게 물어봐요. $it") }
            }
            if (mode == PlayMode.Recommended && !cards[playlist.first()].isFact) {
                val first = cards[playlist.first()]
                say("오늘 하나만 챙긴다면, ${first.line(_state.value.tone)} 좋은 하루 보내세요.")
                _state.update { it.copy(current = playlist.first()) }
            }
        } finally {
            _state.update { it.copy(playing = null, progress = null) }
        }
    }

    private suspend fun say(text: String) = services.voice.speak(text)

    private suspend fun beep() {
        chime?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        delay(300)
    }

    fun stopBriefing() {
        brief?.cancel()
        services.voice.stopSpeaking()
        _state.update { it.copy(playing = null, progress = null) }
    }
}

private const val RECOMMENDED = 2
/** 던지는 말 뒤 쉼: 속으로 따라 말할 시간 (차량 UX 1초 / 음성 UX 1.5초 사이) */
private const val PAUSE_MS = 1_200L
