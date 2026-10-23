package com.smalltoktok.pleos.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
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
 */
class Voice(context: Context) {
    private val app = context.applicationContext
    private val stt = SpeechToText(app, SttMode.ON_DEVICE)
    private val tts = TextToSpeech(app, TtsMode.ON_DEVICE)
    private var ttsReady = CompletableDeferred<Boolean>()
    private var spoken: CompletableDeferred<Unit>? = null
    private var heard: CompletableDeferred<String>? = null
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
            override fun onError(type: SttError) { heard?.complete("") }
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

    /** 다 읽을 때까지 기다린다. 합성이 안 되면 글자 수로 어림한 시간만큼 기다린다 */
    suspend fun speak(text: String) {
        val ready = withTimeoutOrNull(5_000) { ttsReady.await() } ?: false
        if (!ready) return
        val done = CompletableDeferred<Unit>()
        spoken = done
        tts.speak(text, true)
        withTimeoutOrNull(1_500L + text.length * 250L) { done.await() }
    }

    /** 한 번 듣는다. 말이 끝나면(EPD) SDK가 최종 결과를 준다 */
    suspend fun listen(timeoutMs: Long = 20_000): Heard {
        val result = CompletableDeferred<String>()
        heard = result
        _partial.value = ""
        speechOnMs = -1
        speechOffMs = -1
        listenStartedAt = System.currentTimeMillis()
        _listening.value = true
        stt.request()
        val text = withTimeoutOrNull(timeoutMs) { result.await() } ?: run { stt.stop(); "" }
        _listening.value = false
        val delay = if (speechOnMs >= 0) speechOnMs else System.currentTimeMillis() - listenStartedAt
        val speaking = if (speechOnMs >= 0 && speechOffMs > speechOnMs) speechOffMs - speechOnMs else 0
        return Heard(text.trim(), delay, speaking)
    }

    fun stopListening() {
        stt.stop()
        heard?.complete(_partial.value)
    }

    fun stopSpeaking() = tts.stop()

    fun release() {
        runCatching { stt.release() }
        runCatching { tts.release() }
    }

    private companion object {
        const val BYTES_PER_MS = 32L
    }
}
