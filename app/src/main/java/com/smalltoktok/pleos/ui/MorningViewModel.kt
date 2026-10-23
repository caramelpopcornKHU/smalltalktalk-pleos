package com.smalltoktok.pleos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smalltoktok.pleos.Services
import com.smalltoktok.pleos.data.MorningCard
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
)

/** 폰 앱 카테고리 이름 (packages/shared/src/morning.ts MORNING_CATEGORIES) */
val MORNING_CATEGORY_LABELS = mapOf(
    "weather" to "날씨",
    "fun-fact" to "그거 아세요?",
    "season" to "계절",
    "food" to "점심·제철 음식",
    "outing" to "나들이·축제",
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
    }

    fun setTone(tone: Tone) {
        prefs.edit().putString("tone", if (tone == Tone.Stranger) "stranger" else "coworker").apply()
        _state.update { it.copy(tone = tone) }
    }

    fun toggleBriefing() {
        if (brief?.isActive == true) return stopBriefing()
        val cards = _state.value.cards
        if (cards.isEmpty()) return
        brief = viewModelScope.launch {
            try {
                // 짧게: 카테고리 이름과 던지는 말만 (2026-10-23 피드백, 소재 설명·안내 문구는 장황했다)
                cards.forEachIndexed { i, c ->
                    _state.update { it.copy(briefing = i) }
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
