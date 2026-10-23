package com.smalltoktok.pleos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smalltoktok.pleos.Services
import com.smalltoktok.pleos.data.ReflectionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReflectPhase { Telling, Listening, Submitting, Result }

data class ReflectState(
    val phase: ReflectPhase = ReflectPhase.Telling,
    /** 받아쓴 조각들. 제출할 때 마침표로 이어 붙인다 */
    val pieces: List<String> = emptyList(),
    val distance: String? = null,
    val result: ReflectionResult? = null,
    val error: String? = null,
) {
    val story get() = pieces.joinToString(" ") { p -> p.trimEnd().let { if (it.endsWith('.') || it.endsWith('?') || it.endsWith('!')) it else "$it." } }
    val canSubmit get() = story.length >= STORY_MIN
}

/** 서버 reflection 함수의 이야기 길이 제한 (packages/shared REFLECTION_STORY_MIN·MAX) */
const val STORY_MIN = 10
const val STORY_MAX = 1500

/**
 * 아쉬운 대화 (폰 앱 2026-10-21 구현을 차량용으로).
 * 라임 공을 누르고 말하면 Pleos STT가 문장마다 받아쓴다. 말을 멈추고 8초가 지나거나 공을 다시 누르면 듣기를 멈춘다.
 * 다 말한 뒤 "다 말했어요"를 눌러 한 번에 제출한다(되묻지 않는다). 결과는 정차 중에 보고, 주행 중에는 소리로 듣는다.
 */
class ReflectViewModel(app: Application) : AndroidViewModel(app) {
    private val services = Services.get(app)
    private val api = services.api
    val voice = services.voice

    private val _state = MutableStateFlow(ReflectState())
    val state: StateFlow<ReflectState> = _state
    private var job: Job? = null
    private var stopRequested = false

    fun toggleListening() {
        if (_state.value.phase == ReflectPhase.Listening) {
            stopRequested = true
            voice.stopListening()
            return
        }
        job?.cancel()
        stopRequested = false
        _state.update { it.copy(phase = ReflectPhase.Listening, error = null) }
        job = viewModelScope.launch {
            try {
                while (true) {
                    val heard = voice.listen(timeoutMs = SILENCE_STOP_MS)
                    if (heard.text.isNotEmpty()) _state.update { it.copy(pieces = it.pieces + heard.text) }
                    if (heard.text.isEmpty() || stopRequested) break
                    if (_state.value.story.length >= STORY_MAX) break
                }
            } finally {
                _state.update { if (it.phase == ReflectPhase.Listening) it.copy(phase = ReflectPhase.Telling) else it }
            }
        }
    }

    /** 방금 말한 부분 지우기 */
    fun undoLast() = _state.update { it.copy(pieces = it.pieces.dropLast(1)) }

    fun setDistance(slug: String?) = _state.update { it.copy(distance = slug) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        job?.cancel()
        voice.stopListening()
        _state.update { it.copy(phase = ReflectPhase.Submitting, error = null) }
        job = viewModelScope.launch {
            runCatching { api.reflect(s.story.take(STORY_MAX), s.distance) }
                .onSuccess { r -> _state.update { it.copy(phase = ReflectPhase.Result, result = r) } }
                .onFailure { e -> _state.update { it.copy(phase = ReflectPhase.Telling, error = friendlyError(e)) } }
        }
    }

    /** 추천 한마디 들어보기: 서버 목소리, 안 되면 Pleos TTS */
    fun hearLine(index: Int) {
        val r = _state.value.result ?: return
        val line = r.lines.getOrNull(index) ?: return
        job?.cancel()
        job = viewModelScope.launch {
            voice.speak(line.line, api.reflectionLineAudio(r.reflectionId, index))
        }
    }

    /** 주행 중에는 결과를 읽어 준다: 요약 → 추천 한마디 세 개 */
    fun hearAll() {
        val r = _state.value.result ?: return
        job?.cancel()
        job = viewModelScope.launch {
            voice.speak(r.summary)
            r.lines.forEachIndexed { i, l ->
                voice.speak("${i + 1}번째 한마디.")
                voice.speak(l.line, api.reflectionLineAudio(r.reflectionId, i))
            }
        }
    }

    fun reset() {
        job?.cancel()
        voice.stopSpeaking()
        _state.value = ReflectState()
    }

    private companion object {
        const val SILENCE_STOP_MS = 8_000L
    }
}
