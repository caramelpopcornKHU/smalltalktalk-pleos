package com.smalltoktok.pleos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smalltoktok.pleos.Services
import com.smalltoktok.pleos.data.ApiException
import com.smalltoktok.pleos.data.BuddyConfig
import com.smalltoktok.pleos.data.Character
import com.smalltoktok.pleos.data.Feedback
import com.smalltoktok.pleos.data.Message
import com.smalltoktok.pleos.data.Metrics
import com.smalltoktok.pleos.data.Scenario
import com.smalltoktok.pleos.vehicle.Trip
import com.smalltoktok.pleos.vehicle.TripKind
import com.smalltoktok.pleos.vehicle.TripMatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { Idle, Starting, PartnerSpeaking, Listening, Thinking, Finishing, Done, Error }

/** 대화 상대 난이도 (docs/conversation-principles.md 3절). 하 = 이끌어 줌, 중 = 짧게 답함, 상 = 단답·무심 */
enum class Difficulty(val key: String, val label: String) {
    Easy("easy", "하"), Normal("normal", "중"), Hard("hard", "상");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Normal
    }
}

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
    /** 상황 id → 최고 랠리 수. 서버에 남기지 않고 이 기기에만 저장한다(폰 앱과 같다) */
    val rallies: Map<String, Int> = emptyMap(),
    /** 홈 맨 위 "오늘의 상대" */
    val todayId: String? = null,
    /** 존댓말 | 반말 스위치 (2026-10-26). 공유 상황에만 적용하고 이 기기에 기억한다 */
    val casual: Boolean = false,
    /** 스위치 위 한 줄 안내("반말: 말 놓은 또래처럼 대화해요")를 이미 봤는지. 처음 한 번만 보여준다 */
    val speechHintSeen: Boolean = false,
    /** 지금 대화가 반말인지 (대화 화면 위 "반말" 표시) */
    val talkCasual: Boolean = false,
    /** 상대 난이도 하 | 중 | 상 (2026-10-26). 이 기기에 기억한다 */
    val difficulty: Difficulty = Difficulty.Normal,
    /** 지금 대화의 난이도 (대화 화면 위 표시) */
    val talkDifficulty: Difficulty = Difficulty.Normal,
    /** 내비가 안내 중인 목적지 (2026-11-02). 내비가 없거나 안내 중이 아니면 null */
    val trip: Trip? = null,
    /** 목적지에 맞춰 고른 상대. "다른 사람"을 누르면 비우고 무작위 상대로 돌아간다 */
    val tripPartnerId: String? = null,
    /** 말동무 설정 (2026-11-02). 아직 만들지 않았으면 null */
    val buddy: BuddyConfig? = null,
    /** 말동무 사진 고르기용 인물 목록 (설정 화면을 열 때 받는다) */
    val characters: List<Character> = emptyList(),
    /** 지금 대화가 말동무와의 수다인지 (연습이 아니라 코칭 없이 끝난다) */
    val talkBuddy: Boolean = false,
) {
    val shared get() = scenarios.filter { !it.mine }
    val mine get() = scenarios.filter { it.mine }
    val today get() = scenarios.firstOrNull { it.id == todayId }
    val tripPartner get() = scenarios.firstOrNull { it.id == tripPartnerId }
    /** 홈 맨 위 상대: 목적지에 맞는 상대가 있으면 그 사람, 없으면 오늘의 상대 */
    val partner get() = tripPartner ?: today
}

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
    private val records = app.getSharedPreferences("rally", 0)
    private val prefs = app.getSharedPreferences("talk", 0)

    init {
        _state.update { st ->
            st.copy(
                rallies = records.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap(),
                casual = prefs.getBoolean("casual", false),
                speechHintSeen = prefs.getBoolean("speech_hint_seen", false),
                difficulty = Difficulty.of(prefs.getString("difficulty", null)),
                buddy = BuddyConfig.load(prefs),
            )
        }
        loadScenarios()
        // 목적지가 바뀔 때마다 그 자리에 맞는 상대를 다시 고른다
        viewModelScope.launch {
            services.navi.trip.collect { trip ->
                val before = _state.value.trip
                val sameDestination = before != null && trip != null && before.name == trip.name && before.kind == trip.kind
                _state.update { it.copy(trip = trip) }
                // 남은 시간만 바뀐 거면 상대를 그대로 둔다 ("다른 사람"으로 바꾼 상대도 유지)
                if (!sameDestination) matchTrip()
            }
        }
    }

    private fun matchTrip() = _state.update { st ->
        st.copy(tripPartnerId = st.trip?.let { TripMatch.pick(it, st.shared)?.id })
    }

    /** 말투를 바꾼다. 주행 중이면 화면을 다시 보지 않아도 되게 소리로 알려준다 */
    fun setCasual(on: Boolean, announce: Boolean) {
        if (on == _state.value.casual) return
        prefs.edit().putBoolean("casual", on).apply()
        markSpeechHintSeen()
        _state.update { it.copy(casual = on) }
        if (announce) viewModelScope.launch { voice.speak(if (on) "반말로 할게요." else "존댓말로 할게요.") }
    }

    /** 상대 난이도를 바꾼다. 주행 중이면 소리로 알려준다 */
    fun setDifficulty(d: Difficulty, announce: Boolean) {
        if (d == _state.value.difficulty) return
        prefs.edit().putString("difficulty", d.key).apply()
        markSpeechHintSeen()
        _state.update { it.copy(difficulty = d) }
        if (announce) viewModelScope.launch { voice.speak("난이도 ${d.label}으로 할게요.") }
    }

    private fun markSpeechHintSeen() {
        if (_state.value.speechHintSeen) return
        prefs.edit().putBoolean("speech_hint_seen", true).apply()
        _state.update { it.copy(speechHintSeen = true) }
    }

    /** "다른 사람"을 누를 때 꺼내는 순서. 공유 상황 전체를 섞어 한 바퀴 다 보기 전에는 같은 상대가 다시 나오지 않는다 */
    private val todayBag = ArrayDeque<String>()

    /**
     * 오늘의 상대를 무작위로 바꾼다 (2026-10-26).
     * 예전에는 "안 해본 상황 먼저"라 몇 번 해 본 뒤에는 남은 4~5명만 계속 돌았다.
     * 이제는 카드 섞듯 전체를 섞어 차례로 꺼내고(셔플 백), 다 꺼내면 다시 섞는다. 바로 전 상대가 이어 나오지 않게 한다.
     */
    fun shuffleToday() = _state.update { st ->
        val ids = st.shared.map { it.id }
        todayBag.retainAll(ids.toSet())
        if (todayBag.isEmpty()) {
            todayBag.addAll(ids.shuffled())
            // 새로 섞은 첫 장이 방금 본 상대면 맨 뒤로 보낸다
            if (todayBag.size > 1 && todayBag.first() == st.todayId) todayBag.addLast(todayBag.removeFirst())
        }
        st.copy(todayId = todayBag.removeFirstOrNull(), tripPartnerId = null)
    }

    /** 연습을 마치면 이전 기록보다 많이 주고받았을 때만 최고 기록을 바꾼다 */
    private fun recordRally(scenarioId: String, rally: Int) {
        val best = _state.value.rallies[scenarioId]
        if (best != null && best >= rally) return
        records.edit().putInt(scenarioId, rally).apply()
        _state.update { it.copy(rallies = it.rallies + (scenarioId to rally)) }
    }

    fun loadScenarios() {
        _state.update { it.copy(loadingScenarios = true, error = null) }
        viewModelScope.launch {
            runCatching { api.scenarios() }
                .onSuccess { list ->
                    _state.update { it.copy(scenarios = list, loadingScenarios = false, booted = true) }
                    if (_state.value.today == null) shuffleToday()
                    if (_state.value.tripPartnerId == null) matchTrip()
                }
                .onFailure { e -> _state.update { it.copy(loadingScenarios = false, booted = true, error = messageOf(e)) } }
        }
    }

    fun loadCharacters() {
        if (_state.value.characters.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { api.characters() }.onSuccess { list -> _state.update { it.copy(characters = list) } }
        }
    }

    /** 말투만 바꾸면 같은 사람이라 프로필을 그대로 두고, 사진·스타일을 바꾸면 새 사람이라 다시 짓는다 */
    fun saveBuddy(config: BuddyConfig) {
        val before = _state.value.buddy
        val same = before != null && before.characterId == config.characterId && before.style == config.style
        val next = config.copy(persona = if (same) before?.persona else null)
        next.save(prefs)
        _state.update { it.copy(buddy = next) }
    }

    /**
     * 말동무와 수다 (2026-11-02): 연습과 같은 듣기·말하기 고리를 쓰되, 말동무가 자기 일상이나 지금 상황으로 먼저 말을 걸고
     * 최대 20번까지 이어 간다. 끝나면 코칭 없이 홈으로 돌아간다(평가받는 느낌을 없앤다).
     */
    fun startBuddy() {
        val config = _state.value.buddy ?: return
        loop?.cancel()
        metrics.clear()
        conversationId = null
        val scenario = Scenario(
            id = BUDDY_ID, title = "말동무", who = config.style.label, whoShort = "말동무", place = "",
            characterId = config.characterId, firstSay = null, kind = BUDDY_ID,
        )
        _state.update {
            it.copy(
                scenario = scenario, phase = Phase.Starting, messages = emptyList(), partnerTurns = 0,
                feedback = null, error = null, talkCasual = config.casual, talkBuddy = true,
            )
        }
        loop = viewModelScope.launch {
            try {
                // 출퇴근길이면 알려 주고(목적지 이름은 보내지 않는다), 처음이면 서버가 지은 프로필을 저장해 둔다
                val trip = when (_state.value.trip?.kind) {
                    TripKind.ToOffice -> "office"
                    TripKind.ToHome -> "home"
                    else -> null
                }
                val (started, persona) = api.buddyStart(config.characterId, config.style.key, config.casual, config.persona, trip)
                if (config.persona == null && persona != null) {
                    val saved = config.copy(persona = persona).also { it.save(prefs) }
                    _state.update { it.copy(buddy = saved) }
                }
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
                    _state.update { it.copy(phase = Phase.Thinking) }
                    val next = api.buddyTurn(started.conversationId, _state.value.messages)
                    say = next.say
                    audio = next.audioUrl
                    end = next.end
                }
                backHome()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(phase = Phase.Error, error = messageOf(e)) }
            }
        }
    }

    fun start(scenario: Scenario) {
        loop?.cancel()
        metrics.clear()
        conversationId = null
        markSpeechHintSeen()
        val casual = scenario.casual(_state.value.casual)
        _state.update {
            it.copy(
                scenario = scenario, phase = Phase.Starting, messages = emptyList(), partnerTurns = 0,
                feedback = null, error = null, talkCasual = casual, talkDifficulty = it.difficulty, talkBuddy = false,
            )
        }
        loop = viewModelScope.launch {
            try {
                val started = api.start(scenario.id, casual = !scenario.mine && _state.value.casual, difficulty = _state.value.difficulty.key)
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
        // 말동무와의 수다는 코칭 없이 바로 홈으로
        if (_state.value.talkBuddy) return backHome()
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
        _state.value.scenario?.let { recordRally(it.id, rallyOf(msgs)) }
        _state.update { it.copy(phase = Phase.Done, feedback = feedback) }
    }

    /** 랠리: 끊기지 않고(시간 안에 대답하며) 주고받은 가장 긴 횟수 */
    private fun rallyOf(msgs: List<Message>): Int {
        var best = 0
        var run = 0
        msgs.filter { it.speaker == "me" }.forEach { m ->
            run = if (m.text.isNotEmpty()) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }

    /**
     * 대화 습관 리포트의 "이 습관으로 연습하기": 가장 약한 관계 거리의 공유 상황 중 안 해본 것을 먼저.
     * 거리를 모르면 오늘의 상대로 시작한다.
     */
    fun startForDistance(distance: String?) {
        val st = _state.value
        val pool = st.shared.filter { distance == null || it.distance == distance }
        val pick = pool.filter { it.id !in st.rallies }.randomOrNull() ?: pool.randomOrNull() ?: st.partner ?: st.shared.randomOrNull()
        pick?.let { start(it) }
    }

    /**
     * 앱이 화면에서 사라지면(다른 앱 전체화면 등) 마이크와 소리를 멈춘다 (Pleos 앱 구조 가이드: 백그라운드 작업 정리).
     * 대화 중이었으면 지금까지 주고받은 것으로 코칭을 받아 두고, 돌아오면 코칭 화면을 보여준다.
     */
    fun onBackground() {
        if (_state.value.phase in setOf(Phase.Starting, Phase.PartnerSpeaking, Phase.Listening, Phase.Thinking)) endNow()
        else voice.stopSpeaking()
    }

    /** 코칭의 추천 한마디 들어보기 (Pleos TTS) */
    fun say(text: String) {
        viewModelScope.launch { voice.speak(text) }
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

    private companion object {
        /** 말동무 대화의 가짜 상황 id (상황 목록·랠리 기록에는 들어가지 않는다) */
        const val BUDDY_ID = "buddy"
    }

    /** 아쉬운 대화로 만든 연습 상황처럼 목록에 없던 시나리오가 생기면 목록을 새로 받는다 */
    fun startNew(scenario: Scenario) {
        loadScenarios()
        start(scenario)
    }
}
