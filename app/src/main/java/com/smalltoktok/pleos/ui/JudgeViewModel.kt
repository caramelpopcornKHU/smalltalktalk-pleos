package com.smalltoktok.pleos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smalltoktok.pleos.Services
import com.smalltoktok.pleos.data.Verdict
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 말을 받는 칸: 위의 논쟁 주제, 왼쪽 A, 오른쪽 B */
enum class JudgeSlot { Topic, A, B }

data class JudgeState(
    val topic: String = "",
    val a: String = "",
    val b: String = "",
    /** 재심에서 덧붙인 반박 (재심 청구 뒤에 A·B 공을 누르면 여기에 받는다) */
    val appealA: String = "",
    val appealB: String = "",
    /** 지금 듣는 칸 */
    val listening: JudgeSlot? = null,
    val judging: Boolean = false,
    val verdict: Verdict? = null,
    /** 재심 청구 중 (한 번만) */
    val appealing: Boolean = false,
    /** 재심을 이미 했는지 */
    val appealed: Boolean = false,
    /** 재심 전 판결 (재심 요청에 같이 보낸다) */
    val previous: Verdict? = null,
    val error: String? = null,
) {
    val ready get() = topic.isNotBlank() && a.isNotBlank() && b.isNotBlank() &&
        (!appealing || appealA.isNotBlank() || appealB.isNotBlank())
}

/**
 * AI 판사 (2026-10-26, 시안 smalltalktalk docs/prototypes/ai-judge.html).
 * 여럿이 탄 차에서 장난 논쟁을: 주제 공 → A 공 → B 공 → 판결. 판결문은 서버가 만들고 판사 목소리로 읽어 준다.
 * 공을 누르면 조용해질 때까지 받아 적고(여러 문장 이어 붙임), 다시 누르면 멈춘다. 논쟁 내용은 서버에 저장하지 않는다.
 */
class JudgeViewModel(app: Application) : AndroidViewModel(app) {
    private val services = Services.get(app)
    private val voice = services.voice
    private val _state = MutableStateFlow(JudgeState())
    val state: StateFlow<JudgeState> = _state
    private var job: Job? = null
    private var stopRequested = false

    /** 공을 누르면 그 칸을 듣는다. 듣는 중에 같은 공을 누르면 멈추고, 다른 공을 누르면 그 칸으로 옮긴다 */
    fun toggle(slot: JudgeSlot) {
        val current = _state.value.listening
        if (current != null) {
            stopRequested = true
            voice.stopListening()
            if (current == slot) return
        }
        job?.cancel()
        voice.stopSpeaking()
        stopRequested = false
        // 다시 누르면 처음부터 다시 받는다 (재심 중 A·B는 반박 칸)
        _state.update { it.withText(slot, "").copy(listening = slot, error = null) }
        job = viewModelScope.launch {
            try {
                while (true) {
                    val heard = voice.listen(startTimeoutMs = SILENCE_STOP_MS)
                    if (heard.text.isNotEmpty()) {
                        _state.update { st ->
                            val before = st.textOf(slot)
                            st.withText(slot, if (before.isEmpty()) heard.text else "$before ${heard.text}")
                        }
                    }
                    if (heard.text.isEmpty() || stopRequested || _state.value.textOf(slot).length >= MAX_CHARS) break
                }
            } finally {
                _state.update { if (it.listening == slot) it.copy(listening = null) else it }
            }
        }
    }

    /** 판결: 망치를 두드리는 동안 서버가 판결문을 만들고, 판사 목소리로 읽어 준다 */
    fun judge() {
        val st = _state.value
        if (!st.ready || st.judging) return
        if (st.listening != null) {
            stopRequested = true
            voice.stopListening()
        }
        job?.cancel()
        _state.update { it.copy(listening = null, judging = true, error = null) }
        job = viewModelScope.launch {
            val result = runCatching {
                services.api.judge(
                    topic = st.topic, a = st.a, b = st.b,
                    previous = if (st.appealing) st.previous?.let { "${it.title}. ${it.reason}" } else null,
                    appealA = st.appealA.takeIf { st.appealing && it.isNotBlank() },
                    appealB = st.appealB.takeIf { st.appealing && it.isNotBlank() },
                )
            }
            result.onSuccess { v ->
                _state.update {
                    it.copy(judging = false, verdict = v, appealed = it.appealed || it.appealing, appealing = false)
                }
                voice.speak("${v.title}. ${v.reason}", v.audioUrl)
            }.onFailure { e -> _state.update { it.copy(judging = false, error = friendlyError(e)) } }
        }
    }

    /** 재심 청구: 판결문을 닫고, A·B 공으로 반박을 한 번 더 받는다 */
    fun appeal() {
        voice.stopSpeaking()
        _state.update { it.copy(appealing = true, previous = it.verdict, verdict = null, appealA = "", appealB = "") }
    }

    /** 판결문을 다시 듣기 */
    fun replay() {
        val v = _state.value.verdict ?: return
        job?.cancel()
        job = viewModelScope.launch { voice.speak("${v.title}. ${v.reason}", v.audioUrl) }
    }

    fun reset() {
        job?.cancel()
        voice.stopSpeaking()
        voice.stopListening()
        _state.value = JudgeState()
    }

    /** 화면에서 사라지면 듣기·읽기를 멈춘다. 받아 적은 말은 그대로 둔다 */
    fun onBackground() {
        _state.value.listening?.let { toggle(it) }
        voice.stopSpeaking()
    }

    private fun JudgeState.textOf(slot: JudgeSlot) = when (slot) {
        JudgeSlot.Topic -> topic
        JudgeSlot.A -> if (appealing) appealA else a
        JudgeSlot.B -> if (appealing) appealB else b
    }

    private fun JudgeState.withText(slot: JudgeSlot, text: String) = when (slot) {
        JudgeSlot.Topic -> copy(topic = text)
        JudgeSlot.A -> if (appealing) copy(appealA = text) else copy(a = text)
        JudgeSlot.B -> if (appealing) copy(appealB = text) else copy(b = text)
    }

    private companion object {
        /** 서버 한도(주제 120자, 주장 600자)보다 넉넉히 작게 */
        const val MAX_CHARS = 500
        /** 이만큼 조용하면 받아쓰기를 끝낸다 (털어놓기와 같은 값) */
        const val SILENCE_STOP_MS = 8_000L
    }
}
