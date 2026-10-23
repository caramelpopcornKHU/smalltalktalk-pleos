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

data class HabitState(
    val loading: Boolean = true,
    val report: HabitReport? = null,
    /** 최근 7일 대화 수 (리포트가 없을 때 "n번 더" 안내용) */
    val weekConversations: Int = 0,
    val speaking: Boolean = false,
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
        if (speech?.isActive == true) toggleSpeak(null)
    }

    fun toggleSpeak(name: String?) {
        if (speech?.isActive == true) {
            speech?.cancel()
            services.voice.stopSpeaking()
            _state.update { it.copy(speaking = false) }
            return
        }
        val report = _state.value.report ?: return
        speech = viewModelScope.launch {
            _state.update { it.copy(speaking = true) }
            try {
                services.voice.speak(report.spoken(name))
            } finally {
                _state.update { it.copy(speaking = false) }
            }
        }
    }
}
