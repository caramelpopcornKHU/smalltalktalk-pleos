package com.smalltoktok.pleos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smalltoktok.pleos.Services
import com.smalltoktok.pleos.data.ApiException
import com.smalltoktok.pleos.data.Feedback
import com.smalltoktok.pleos.data.Message
import com.smalltoktok.pleos.data.Metrics
import com.smalltoktok.pleos.data.Scenario
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { Idle, Starting, PartnerSpeaking, Listening, Thinking, Finishing, Done, Error }

data class TalkState(
    val scenarios: List<Scenario> = emptyList(),
    val loadingScenarios: Boolean = true,
    /** 앱을 켠 뒤 첫 불러오기가 끝났는지. 그 전에는 로딩 화면을 보여준다 */
    val booted: Boolean = false,
    val scenario: Scenario? = null,
    val phase: Phase = Phase.Idle,
    val messages: List<Message> = emptyList(),
    val partnerTurns: Int = 0,
    val maxPartnerTurns: Int = 6,
    val feedback: Feedback? = null,
    val error: String? = null,
)

/**
 * 저녁 연습의 자유 대화를 차량용으로: 상대 말(Pleos TTS) → 내 말(Pleos STT) → 서버가 다음 말을 만든다.
 * 대화 원문은 서버에 저장하지 않고 이 ViewModel 메모리에만 둔다(폰 앱과 같은 원칙).
 * ViewModel이 화면 회전·창 크기 변경을 넘어 살아 있어서, 차량 창이 바뀌어도 대화가 이어진다.
 */
class TalkViewModel(app: Application) : AndroidViewModel(app) {
    private val services = Services.get(app)
    val api = services.api
    val voice = services.voice
    val driving = services.driving

    private val _state = MutableStateFlow(TalkState())
    val state: StateFlow<TalkState> = _state

    private var conversationId: String? = null
    private val metrics = mutableListOf<Metrics>()
    private var loop: Job? = null

    init {
        loadScenarios()
    }

    fun loadScenarios() {
        _state.update { it.copy(loadingScenarios = true, error = null) }
        viewModelScope.launch {
            runCatching { api.scenarios() }
                .onSuccess { list -> _state.update { it.copy(scenarios = list, loadingScenarios = false, booted = true) } }
                .onFailure { e -> _state.update { it.copy(loadingScenarios = false, booted = true, error = messageOf(e)) } }
        }
    }

    fun start(scenario: Scenario) {
        loop?.cancel()
        metrics.clear()
        conversationId = null
        _state.update {
            it.copy(scenario = scenario, phase = Phase.Starting, messages = emptyList(), partnerTurns = 0, feedback = null, error = null)
        }
        loop = viewModelScope.launch {
            try {
                val started = api.start(scenario.id)
                conversationId = started.conversationId
                _state.update { it.copy(maxPartnerTurns = started.maxPartnerTurns) }
                var say = started.firstSay
                var audio = started.firstAudioUrl
                var end = false
                while (true) {
                    partnerSays(say, audio)
                    if (end || _state.value.partnerTurns >= _state.value.maxPartnerTurns) break
                    _state.update { it.copy(phase = Phase.Listening) }
                    val heard = voice.listen()
                    addMessage(Message("me", heard.text))
                    if (heard.text.isNotEmpty()) metrics += Metrics(heard.responseDelayMs, heard.speakingMs)
                    _state.update { it.copy(phase = Phase.Thinking) }
                    val next = api.turn(started.conversationId, _state.value.messages)
                    say = next.say
                    audio = next.audioUrl
                    end = next.end
                }
                finish()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(phase = Phase.Error, error = messageOf(e)) }
            }
        }
    }

    /** 사용자가 대화를 끝낸다. 내 차례까지 주고받은 게 있으면 코칭을 받는다 */
    fun endNow() {
        loop?.cancel()
        voice.stopSpeaking()
        voice.stopListening()
        viewModelScope.launch {
            // 서버는 상대-나가 번갈아 끝난 대화만 받는다. 상대 말로 끝났으면 마지막 상대 말을 뺀다
            val msgs = _state.value.messages.let { if (it.size % 2 == 1) it.dropLast(1) else it }
            if (msgs.size < 2) {
                _state.update { it.copy(phase = Phase.Idle, scenario = null) }
                return@launch
            }
            _state.update { it.copy(messages = msgs) }
            runCatching { finish() }
                .onFailure { e -> _state.update { it.copy(phase = Phase.Error, error = messageOf(e)) } }
        }
    }

    /** 내 말이 하나도 없으면 코칭할 게 없어 처음으로 돌아간다 */
    private suspend fun finish() {
        val id = conversationId ?: return
        val msgs = _state.value.messages.let { if (it.size % 2 == 1) it.dropLast(1) else it }
        if (msgs.size < 2) {
            _state.update { it.copy(phase = Phase.Idle, scenario = null) }
            return
        }
        _state.update { it.copy(phase = Phase.Finishing) }
        val feedback = api.finish(id, msgs, metrics.take(msgs.count { it.speaker == "me" && it.text.isNotEmpty() }))
        _state.update { it.copy(phase = Phase.Done, feedback = feedback) }
    }

    fun backHome() {
        loop?.cancel()
        voice.stopSpeaking()
        _state.update { it.copy(phase = Phase.Idle, scenario = null, feedback = null, error = null) }
    }

    private suspend fun partnerSays(say: String, audioUrl: String?) {
        addMessage(Message("partner", say))
        _state.update { it.copy(phase = Phase.PartnerSpeaking, partnerTurns = it.partnerTurns + 1) }
        voice.speak(say, audioUrl)
    }

    private fun addMessage(m: Message) = _state.update { it.copy(messages = it.messages + m) }

    private fun messageOf(e: Throwable) = friendlyError(e)

    /** 아쉬운 대화로 만든 연습 상황처럼 목록에 없던 시나리오가 생기면 목록을 새로 받는다 */
    fun startNew(scenario: Scenario) {
        loadScenarios()
        start(scenario)
    }
}
