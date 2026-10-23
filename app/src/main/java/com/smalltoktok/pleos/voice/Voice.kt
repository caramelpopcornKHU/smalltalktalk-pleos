package com.smalltoktok.pleos.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/** 한 번 듣기의 결과. text가 비면 시간 안에 말하지 못한 것 */
data class Heard(val text: String, val responseDelayMs: Long, val speakingMs: Long)

/**
 * Pleos SpeechToText·TextToSpeech를 코루틴으로 감싼다.
 * 2026-10-23 에뮬레이터에서 ON_DEVICE 모드 한국어 인식·합성을 앱 등록 없이 확인했다.
 * 주의: TTS는 initialize 성공 뒤에 리스너를 붙여야 한다(먼저 붙이면 SDK 내부 예외).
 *
 * 목소리 (2026-10-23 결정): 인터넷이 되면 폰 앱과 같은 서버 목소리(Google Chirp 3 HD, 상대 성별에 맞춘 Leda/Orus)를
 * 재생하고, 오프라인이거나 파일이 없거나 재생에 실패하면 차량 내장 Pleos TTS(Gleo)로 읽는다.
 * 재생 전 오디오 포커스를 잠깐 받아 다른 소리(음악 등)를 줄인다(Pleos 오디오 포커스 정책).
 */
class Voice(context: Context) {
    private val app = context.applicationContext
    private val stt = SpeechToText(app, SttMode.ON_DEVICE)
    private val tts = TextToSpeech(app, TtsMode.ON_DEVICE)
    private var ttsReady = CompletableDeferred<Boolean>()
    private var spoken: CompletableDeferred<Unit>? = null
    private var heard: CompletableDeferred<String>? = null
    private var speechStarted: CompletableDeferred<Unit>? = null
    private var listenStartedAt = 0L
    private var speechOnMs = -1L
    private var speechOffMs = -1L

    private val _partial = MutableStateFlow("")
    /** 듣는 동안 화면에 보여줄 중간 결과 */
    val partial: StateFlow<String> = _partial

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    fun initialize() {
        stt.addListener(object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) {
                _partial.value = text
                if (text.isNotBlank()) speechStarted?.complete(Unit)
                if (isFinal) heard?.complete(text)
            }
            override fun onUpdatedEpdData(on: Long, off: Long) {
                // EPD 값은 마이크를 연 뒤부터 쌓인 오디오 바이트로 보인다(16kHz·16bit = 1초 32000).
                // 에뮬레이터 로그의 타임스탬프와 맞춰 본 추정이라 실제 차량에서 다시 확인한다
                speechOnMs = on / BYTES_PER_MS
                speechOffMs = off / BYTES_PER_MS
            }
            override fun onStartedRecognition() = Unit
            override fun onEndedRecognition() { _listening.value = false }
            // 오류가 나도 그때까지 받아쓴 말은 버리지 않는다
            override fun onError(type: SttError) { heard?.complete(_partial.value) }
            override fun onReady() = Unit
            override fun onUpdatedRms(rms: Double) = Unit
        })
        stt.initialize(object : SttInit {
            override fun onSuccess() = stt.setLocale(Locale.KOREA)
            override fun onFailure(type: SttError) = Unit
        })
        tts.initialize(object : TtsInit {
            override fun onSuccess() {
                tts.addEventListener(object : EventListener {
                    override fun onStart() = Unit
                    override fun onReady() = Unit
                    override fun onDone() { spoken?.complete(Unit) }
                    override fun onError(type: TtsError) { spoken?.complete(Unit) }
                    override fun onStop() { spoken?.complete(Unit) }
                    override fun onUpdatedRms(rms: Double) = Unit
                })
                tts.setLocale(Locale.KOREA)
                ttsReady.complete(true)
            }
            override fun onFailure(type: TtsError) { ttsReady.complete(false) }
        })
    }

    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var player: MediaPlayer? = null
    private var playDone: CompletableDeferred<Boolean>? = null

    /**
     * 오디오 포커스 변화 (Pleos Audio Focus Guide): LOSS·LOSS_TRANSIENT면 바로 멈추고(길 안내·전화 등에 양보),
     * CAN_DUCK이면 소리를 줄였다가 GAIN에서 되돌린다. 멈춘 문장은 다시 읽지 않는다.
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                runCatching { player?.stop() }
                playDone?.complete(true)
                runCatching { tts.stop() }
                spoken?.complete(Unit)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching { player?.setVolume(DUCK_VOLUME, DUCK_VOLUME) }
            AudioManager.AUDIOFOCUS_GAIN -> runCatching { player?.setVolume(1f, 1f) }
        }
    }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAttributes)
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    private fun online(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** 서버 목소리가 있으면 그걸로, 아니면 Pleos TTS로 다 읽을 때까지 기다린다 */
    suspend fun speak(text: String, audioUrl: String? = null) {
        // 포커스를 못 받으면(전화 중 등) 읽지 않는다
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return
        try {
            if (audioUrl != null && online() && playUrl(audioUrl)) return
            speakOnDevice(text)
        } finally {
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    /** 끝까지 재생하면 true, 준비·재생에 실패하면 false (그때는 Pleos TTS로 읽는다) */
    private suspend fun playUrl(url: String): Boolean {
        val done = CompletableDeferred<Boolean>()
        playDone = done
        val mp = MediaPlayer().apply {
            setAudioAttributes(speechAttributes)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { done.complete(true) }
            setOnErrorListener { _, _, _ -> done.complete(false); true }
        }
        player = mp
        return try {
            mp.setDataSource(url)
            mp.prepareAsync()
            withTimeoutOrNull(30_000) { done.await() } ?: false
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        } finally {
            player = null
            playDone = null
            mp.release()
        }
    }

    /** 다 읽을 때까지 기다린다. 합성이 안 되면 글자 수로 어림한 시간만큼 기다린다 */
    private suspend fun speakOnDevice(text: String) {
        val ready = withTimeoutOrNull(5_000) { ttsReady.await() } ?: false
        if (!ready) return
        val done = CompletableDeferred<Unit>()
        spoken = done
        tts.speak(text, true)
        withTimeoutOrNull(1_500L + text.length * 250L) { done.await() }
    }

    /**
     * 한 문장을 듣는다. 말이 끝나면(EPD) SDK가 최종 결과를 준다.
     * startTimeoutMs: 이 시간 안에 말을 시작하지 않으면 그만 듣는다(빈 결과).
     * 말을 시작한 뒤에는 길게 말해도 끊지 않는다. maxSpeechMs를 넘기면 그때까지 받아쓴 말을 돌려준다.
     * (2026-10-23 수정: 처음에는 전체 시간에 제한을 걸어, 길게 다다다 말하면 문장이 통째로 버려졌다)
     */
    suspend fun listen(startTimeoutMs: Long = 20_000, maxSpeechMs: Long = 120_000): Heard {
        val result = CompletableDeferred<String>()
        val started = CompletableDeferred<Unit>()
        heard = result
        speechStarted = started
        _partial.value = ""
        speechOnMs = -1
        speechOffMs = -1
        listenStartedAt = System.currentTimeMillis()
        _listening.value = true
        stt.request()
        val begun = withTimeoutOrNull(startTimeoutMs) {
            kotlinx.coroutines.selects.select<Unit> {
                started.onAwait { }
                result.onAwait { }
            }
        } != null
        val text = if (!begun) {
            stt.stop()
            ""
        } else {
            withTimeoutOrNull(maxSpeechMs) { result.await() } ?: run { stt.stop(); _partial.value }
        }
        _listening.value = false
        val delay = if (speechOnMs >= 0) speechOnMs else System.currentTimeMillis() - listenStartedAt
        val speaking = if (speechOnMs >= 0 && speechOffMs > speechOnMs) speechOffMs - speechOnMs else 0
        return Heard(text.trim(), delay, speaking)
    }

    fun stopListening() {
        stt.stop()
        heard?.complete(_partial.value)
    }

    fun stopSpeaking() {
        runCatching { player?.stop() }
        runCatching { tts.stop() }
    }

    fun release() {
        runCatching { stt.release() }
        runCatching { tts.release() }
    }

    private companion object {
        const val BYTES_PER_MS = 32L
        const val DUCK_VOLUME = 0.3f
    }
}
