package com.smalltoktok.pleos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smalltoktok.pleos.Services
import com.smalltoktok.pleos.data.MorningCard
import com.smalltoktok.pleos.data.Tip
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Tone { Coworker, Stranger }

data class MorningState(
    val loading: Boolean = true,
    val cards: List<MorningCard> = emptyList(),
    val tone: Tone = Tone.Coworker,
    /** 브리핑 중 지금 읽는 카드 */
    val briefing: Int? = null,
    val error: String? = null,
    /** 스몰토크 잘하는 법 영상·글 (2026-10-23) */
    val tips: List<Tip> = emptyList(),
    val filter: FeedFilter = FeedFilter.All,
    /** 열어 둔 영상(앱 안 플레이어) 또는 글(읽어주기) */
    val openTip: Tip? = null,
    /** 글을 읽는 중이면 지금 읽는 문단 번호 */
    val readingParagraph: Int? = null,
) {
    /** 아침 카드 목록에 영상·글을 섞는다: 소재 두 장마다 팁 하나, 남는 팁은 뒤에 */
    val feed: List<FeedItem> get() {
        val topics = cards.map { FeedItem.Topic(it) }
        val extra = tips.map { FeedItem.TipItem(it) }
        return when (filter) {
            FeedFilter.Topics -> topics
            FeedFilter.Videos -> extra.filter { it.tip.isVideo }
            FeedFilter.Articles -> extra.filter { !it.tip.isVideo }
            FeedFilter.All -> buildList {
                val rest = ArrayDeque(extra)
                topics.forEachIndexed { i, t ->
                    add(t)
                    if (i % 2 == 1) rest.removeFirstOrNull()?.let(::add)
                }
                addAll(rest)
            }
        }
    }
}

enum class FeedFilter(val label: String) { All("전체"), Topics("오늘의 소재"), Videos("영상"), Articles("읽어주는 글") }

sealed interface FeedItem {
    val key: String
    data class Topic(val card: MorningCard) : FeedItem { override val key get() = card.id }
    data class TipItem(val tip: Tip) : FeedItem { override val key get() = tip.id }
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
)

fun MorningCard.line(tone: Tone) = if (tone == Tone.Coworker) coworkerLine else strangerLine

/**
 * 아침 카드 (폰 앱과 같은 데이터: 소재 한 줄 / 던지는 말 / 이어가는 말, 동료에게·처음 보는 사람에게 토글).
 * 차량에서는 출근길에 "브리핑 듣기"로 카드를 차례로 읽어 준다(Pleos TTS). 카드 목록은 정차 중에 본다.
 */
class MorningViewModel(app: Application) : AndroidViewModel(app) {
    private val services = Services.get(app)
    private val prefs = app.getSharedPreferences("morning", 0)

    private val _state = MutableStateFlow(
        MorningState(tone = if (prefs.getString("tone", null) == "stranger") Tone.Stranger else Tone.Coworker),
    )
    val state: StateFlow<MorningState> = _state
    private var brief: Job? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { services.api.morningCards() }
                .onSuccess { cards -> _state.update { it.copy(loading = false, cards = cards) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
        // 영상·글은 실패해도 아침 카드는 그대로 보여준다
        viewModelScope.launch {
            runCatching { services.api.tips() }.onSuccess { tips -> _state.update { it.copy(tips = tips) } }
        }
    }

    fun setFilter(f: FeedFilter) = _state.update { it.copy(filter = f) }

    /** 영상은 정차 중에만 연다(Pleos: 주행 중 동영상 금지). 글은 열자마자 읽기 시작 */
    fun open(tip: Tip) {
        stopBriefing()
        _state.update { it.copy(openTip = tip, readingParagraph = null) }
        if (!tip.isVideo) readArticle(tip)
    }

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

    /**
     * 추천 3장: 날씨는 늘 첫 장, 나머지 두 장은 그날 날짜로 섞어 고른다(같은 날에는 같은 추천).
     * 출근길이 짧을 때 듣는 용도이고, 다 듣고 싶으면 "전부 듣기".
     */
    fun recommended(cards: List<MorningCard>): List<MorningCard> {
        val (weather, others) = cards.partition { it.category == "weather" }
        val seed = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toEpochDay()
        return (weather.take(1) + others.shuffled(java.util.Random(seed))).take(RECOMMENDED)
    }

    /** all=false면 추천 3장, true면 전부. 듣는 중에 누르면 멈춘다 */
    fun toggleBriefing(all: Boolean) {
        if (brief?.isActive == true) return stopBriefing()
        val cards = _state.value.cards
        if (cards.isEmpty()) return
        val playlist = if (all) cards else recommended(cards)
        brief = viewModelScope.launch {
            try {
                // 짧게: 카테고리 이름과 던지는 말만 (2026-10-23 피드백, 소재 설명·안내 문구는 장황했다)
                playlist.forEach { c ->
                    _state.update { it.copy(briefing = cards.indexOf(c)) }
                    val label = MORNING_CATEGORY_LABELS[c.category]?.let { "$it. " } ?: ""
                    services.voice.speak(label + c.line(_state.value.tone))
                }
            } finally {
                _state.update { it.copy(briefing = null) }
            }
        }
    }

    fun stopBriefing() {
        brief?.cancel()
        services.voice.stopSpeaking()
        _state.update { it.copy(briefing = null) }
    }
}

private const val RECOMMENDED = 3
