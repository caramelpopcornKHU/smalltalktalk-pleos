package com.smalltoktok.pleos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smalltoktok.pleos.Services
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 리포트 읽어 주기 두 가지 (2026-10-26): 평범하게 / 팩트폭행(반말 친구) */
enum class ReadMode { Plain, Roast }

data class HabitState(
    val loading: Boolean = true,
    val report: HabitReport? = null,
    /** 최근 7일 대화 수 (리포트가 없을 때 "n번 더" 안내용) */
    val weekConversations: Int = 0,
    /** 지금 읽고 있는 방식 (없으면 null) */
    val reading: ReadMode? = null,
    /** 팩트폭행 문장을 서버에서 만드는 중 */
    val roastLoading: Boolean = false,
    /** 마지막으로 들은 팩트폭행 (정차 중 화면에 글로도 보여준다) */
    val roastText: String? = null,
    val error: String? = null,
)

/** 나의 대화 습관 탭. 최근 14일 요약 태그를 받아 이번 주와 지난주를 비교한다 */
class HabitViewModel(app: Application) : AndroidViewModel(app) {
    private val services = Services.get(app)
    private val _state = MutableStateFlow(HabitState())
    val state: StateFlow<HabitState> = _state
    private var speech: Job? = null

    init {
        load()
    }

    /** 탭에 들어올 때마다 다시 불러 방금 끝낸 대화도 반영한다 */
    fun load() {
        _state.update { it.copy(loading = it.report == null, error = null) }
        viewModelScope.launch {
            runCatching { services.api.habitRecords(days = 14) }
                .onSuccess { records ->
                    val weekAgo = java.time.Instant.now().minus(java.time.Duration.ofDays(7))
                    _state.update {
                        it.copy(
                            loading = false,
                            report = buildReport(records),
                            weekConversations = records.count { r -> r.startedAt >= weekAgo },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun stopSpeaking() {
        speech?.cancel()
        services.voice.stopSpeaking()
        _state.update { it.copy(reading = null, roastLoading = false) }
    }

    /** 같은 칩을 다시 누르면 멈추고, 다른 칩을 누르면 바꿔 읽는다 */
    fun read(mode: ReadMode, name: String?) {
        val current = _state.value.reading
        stopSpeaking()
        if (current == mode) return
        val report = _state.value.report ?: return
        speech = viewModelScope.launch {
            _state.update { it.copy(reading = mode, error = null) }
            try {
                when (mode) {
                    ReadMode.Plain -> services.voice.speak(report.spoken(name))
                    ReadMode.Roast -> {
                        _state.update { it.copy(roastLoading = true) }
                        val roast = runCatching { services.api.roast() }
                            .onFailure { e -> _state.update { it.copy(error = friendlyError(e)) } }
                            .getOrNull()
                        _state.update { it.copy(roastLoading = false, roastText = roast?.text ?: it.roastText) }
                        roast?.let { services.voice.speak(it.text, it.audioUrl) }
                    }
                }
            } finally {
                _state.update { it.copy(reading = null, roastLoading = false) }
            }
        }
    }
}
