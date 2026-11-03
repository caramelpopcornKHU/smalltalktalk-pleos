package com.smalltoktok.pleos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.smalltoktok.pleos.Services
import com.smalltoktok.pleos.data.ApiException
import com.smalltoktok.pleos.data.ReflectionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** "이런 순간이었나요?" 장면 칩 (화면을 열 때마다 무작위) */
    val scenes: List<SceneStarter> = pickScenes(SCENE_COUNT),
    /** 기다리는 동안 보여주는 단계 (WAIT_STEPS 번호) */
    val waitStep: Int = 0,
    /** 지난 아쉬운 대화 */
    val past: List<ReflectionResult> = emptyList(),
    /** "고쳐서 다시 만들기"로 다시 만드는 중인 이전 기록 (새로 만들면 지운다) */
    val replacing: ReflectionResult? = null,
) {
    val story get() = pieces.joinToString(" ") { p ->
        p.trimEnd().let { if (it.endsWith('.') || it.endsWith('?') || it.endsWith('!') || it.endsWith('…')) it else "$it." }
    }
    val canSubmit get() = story.length >= STORY_MIN
    /** 10자까지 남은 글자 수 */
    val need get() = (STORY_MIN - story.length).coerceAtLeast(0)
}

/** 서버 reflection 함수의 이야기 길이 제한 (packages/shared REFLECTION_STORY_MIN·MAX) */
const val STORY_MIN = 10
const val STORY_MAX = 1500
const val SCENE_COUNT = 4

/** 기다리는 동안 차례로 보여주는 단계 (폰 앱과 같다. 실제 생성은 약 10초) */
val WAIT_STEPS = listOf(
    "이야기를 정리하는 중이에요",
    "그때 해볼 만한 한마디를 고르는 중이에요",
    "상대의 첫 마디를 준비하는 중이에요",
)

/**
 * 아쉬운 대화 (폰 앱 2026-10-21 구현을 차량용으로).
 * 라임 공을 누르고 말하면 Pleos STT가 문장마다 받아쓴다. 막히면 "이런 순간이었나요?" 장면 칩으로 첫마디를 채우고 이어 말한다.
 * 다 말한 뒤 한 번에 제출한다(되묻지 않는다). 결과 맨 위에 "이렇게 이해했어요"를 보여주고 다르면 고쳐서 다시 만든다.
 * 결과는 정차 중에 보고, 주행 중에는 소리로 듣는다. 아래에 지난 기록.
 */
class ReflectViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val services = Services.get(app)
    private val api = services.api
    val voice = services.voice

    // 받아쓴 조각과 거리는 프로세스가 다시 만들어져도 남게 SavedStateHandle에 둔다 (Pleos: 진행 상태 복원)
    private val _state = MutableStateFlow(
        ReflectState(pieces = saved.get<ArrayList<String>>("pieces").orEmpty(), distance = saved.get<String>("distance")),
    )
    val state: StateFlow<ReflectState> = _state
    private var job: Job? = null
    private var stopRequested = false

    init {
        viewModelScope.launch {
            _state.collect {
                saved["pieces"] = ArrayList(it.pieces)
                saved["distance"] = it.distance
            }
        }
        loadPast()
    }

    fun loadPast() {
        viewModelScope.launch {
            runCatching { api.reflections() }.onSuccess { list -> _state.update { it.copy(past = list) } }
        }
    }

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
                voice.dictate(shouldStop = { stopRequested || _state.value.story.length >= STORY_MAX }) { piece ->
                    _state.update { it.copy(pieces = it.pieces + piece) }
                }
            } finally {
                _state.update { if (it.phase == ReflectPhase.Listening) it.copy(phase = ReflectPhase.Telling) else it }
            }
        }
    }

    /** 장면 칩: 첫마디를 채우고 바로 이어 듣는다 (차에서는 손을 덜 쓰게) */
    fun useScene(scene: SceneStarter) {
        _state.update { it.copy(pieces = listOf(scene.starter)) }
        toggleListening()
    }

    fun moreScenes() = _state.update { it.copy(scenes = pickScenes(SCENE_COUNT, it.scenes)) }

    /** 화면에서 사라지면 듣기·읽기를 멈춘다. 받아쓴 조각은 그대로 둔다 */
    fun onBackground() {
        if (_state.value.phase == ReflectPhase.Listening) toggleListening()
        voice.stopSpeaking()
    }

    /** 방금 말한 부분 지우기 */
    fun undoLast() = _state.update { it.copy(pieces = it.pieces.dropLast(1)) }

    fun setDistance(slug: String?) = _state.update { it.copy(distance = slug) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        job?.cancel()
        voice.stopListening()
        _state.update { it.copy(phase = ReflectPhase.Submitting, error = null, waitStep = 0) }
        val steps = viewModelScope.launch {
            while (true) {
                delay(WAIT_STEP_MS)
                _state.update { it.copy(waitStep = (it.waitStep + 1).coerceAtMost(WAIT_STEPS.lastIndex)) }
            }
        }
        job = viewModelScope.launch {
            runCatching { api.reflect(s.story.take(STORY_MAX), s.distance) }
                .onSuccess { r ->
                    // 고쳐서 다시 만들었으면 이전 기록은 지운다 (폰 앱과 같다)
                    s.replacing?.let { old -> runCatching { api.deleteReflection(old) } }
                    _state.update { it.copy(phase = ReflectPhase.Result, result = r, pieces = emptyList(), distance = null, replacing = null) }
                    loadPast()
                }
                .onFailure { e -> _state.update { it.copy(phase = ReflectPhase.Telling, error = errorCopy(e)) } }
            steps.cancel()
        }
    }

    /** "다르게 이해했다면 고쳐서 다시 만들기": 원문을 다시 이야기로 두고, 더 말하거나 고친 뒤 다시 제출한다 */
    fun editAndRetry(r: ReflectionResult) {
        voice.stopSpeaking()
        _state.update {
            it.copy(phase = ReflectPhase.Telling, pieces = listOf(r.inputText), replacing = r, result = null, error = null)
        }
    }

    fun deletePast(r: ReflectionResult) {
        viewModelScope.launch {
            runCatching { api.deleteReflection(r) }
                .onSuccess { _state.update { it.copy(past = it.past - r) } }
                .onFailure { e -> _state.update { it.copy(error = friendlyError(e)) } }
        }
    }

    /** 추천 한마디 들어보기: 서버 목소리, 안 되면 Pleos TTS */
    fun hearLine(r: ReflectionResult, index: Int) {
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

    /** 다른 이야기 털어놓기 */
    fun reset() {
        job?.cancel()
        voice.stopSpeaking()
        _state.update { ReflectState(past = it.past, scenes = pickScenes(SCENE_COUNT, it.scenes)) }
    }

    /** 폰 앱과 같은 안내 문구 */
    private fun errorCopy(e: Throwable): String = when ((e as? ApiException)?.code) {
        "quota_exceeded" -> "체험으로 만들 수 있는 연습은 여기까지예요."
        "llm_refusal" -> "이 이야기로는 연습을 만들기 어려웠어요. 그때 오간 말을 조금만 더 들려주실래요?"
        else -> if (e is java.io.IOException) "연결이 잠깐 끊겼어요. 다시 눌러 주세요." else friendlyError(e)
    }

    private companion object {
        const val WAIT_STEP_MS = 3_500L
    }
}
