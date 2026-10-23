package com.hyundai.SmalltalktalkPleos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/**
 * 음성 기능 확인용 임시 화면.
 * Pleos STT·TTS를 ON_DEVICE 모드(앱 등록 없이 동작)로 한국어 테스트하고,
 * 대안인 안드로이드 기본 음성 인식(SpeechRecognizer)이 이 기기에 있는지도 본다.
 */
class VoiceTestActivity : AppCompatActivity() {

    private lateinit var log: TextView
    private var stt: SpeechToText? = null
    private var tts: TextToSpeech? = null
    private var androidStt: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply { textSize = 16f; setPadding(32, 32, 32, 32) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(button("1. Pleos STT 초기화 (ON_DEVICE, ko-KR)") { safely("STT 초기화") { initStt() } })
            addView(button("2. Pleos STT 듣기 시작") { safely("STT 듣기") { stt?.request() ?: line("STT 먼저 초기화") } })
            addView(button("3. Pleos STT 멈추기") { safely("STT 멈추기") { stt?.stop() } })
            addView(button("4. Pleos TTS 초기화 (ON_DEVICE, ko-KR)") { safely("TTS 초기화") { initTts() } })
            addView(button("5. Pleos TTS 말하기") {
                safely("TTS 말하기") { tts?.speak("안녕하세요. 오늘 출근길 어떠셨어요?", true) ?: line("TTS 먼저 초기화") }
            })
            addView(button("6. 안드로이드 기본 음성 인식 확인") { safely("기본 인식") { tryAndroidStt() } })
            addView(ScrollView(this@VoiceTestActivity).apply { addView(log) })
        }
        setContentView(root)
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
        logPermissions()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        logPermissions()
    }

    private fun logPermissions() = PERMISSIONS.forEach {
        line("권한 ${it.substringAfterLast('.')}: " +
            (ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED))
    }

    /** SDK가 권한 부족 등으로 예외를 던져도 앱이 죽지 않고 로그에 남게 한다 */
    private fun safely(label: String, block: () -> Unit) = try {
        block()
    } catch (e: Exception) {
        line("$label 예외: ${e.javaClass.simpleName}: ${e.message}")
    }

    private fun initStt() {
        stt?.release()
        val s = SpeechToText(applicationContext, SttMode.ON_DEVICE)
        stt = s
        s.addListener(object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) = line("STT ${if (isFinal) "최종" else "중간"}: $text")
            override fun onUpdatedEpdData(on: Long, off: Long) = line("STT 말 시작/끝: $on / $off")
            override fun onStartedRecognition() = line("STT 인식 시작")
            override fun onEndedRecognition() = line("STT 인식 끝")
            override fun onError(type: SttError) = line("STT 오류: $type")
            override fun onReady() = line("STT 준비")
            override fun onUpdatedRms(rms: Double) = Unit
        })
        s.initialize(object : SttInit {
            override fun onSuccess() {
                line("STT 초기화 성공")
                s.setLocale(Locale.KOREA)
            }
            override fun onFailure(type: SttError) = line("STT 초기화 실패: $type")
        })
    }

    private fun initTts() {
        tts?.release()
        val t = TextToSpeech(applicationContext, TtsMode.ON_DEVICE)
        tts = t
        t.addEventListener(object : EventListener {
            override fun onStart() = line("TTS 시작")
            override fun onReady() = line("TTS 준비")
            override fun onDone() = line("TTS 끝")
            override fun onError(type: TtsError) = line("TTS 오류: $type")
            override fun onStop() = line("TTS 멈춤")
            override fun onUpdatedRms(rms: Double) = Unit
        })
        t.initialize(object : TtsInit {
            override fun onSuccess() {
                line("TTS 초기화 성공")
                t.setLocale(Locale.KOREA)
            }
            override fun onFailure(type: TtsError) = line("TTS 초기화 실패: $type")
        })
    }

    private fun tryAndroidStt() {
        val available = SpeechRecognizer.isRecognitionAvailable(this)
        line("안드로이드 기본 음성 인식 있음: $available")
        if (!available) return
        androidStt?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        androidStt = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = line("기본 인식 준비, 말해 보세요")
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = line("기본 인식 말 끝")
            override fun onError(error: Int) = line("기본 인식 오류 코드: $error")
            override fun onResults(results: Bundle?) =
                line("기본 인식 결과: " + results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        })
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; isAllCaps = false; setOnClickListener { onClick() } }

    private fun line(msg: String) = runOnUiThread {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        log.append("[$time] $msg\n")
    }

    override fun onDestroy() {
        stt?.release()
        tts?.release()
        androidStt?.destroy()
        super.onDestroy()
    }
}

private val PERMISSIONS = listOf(
    Manifest.permission.RECORD_AUDIO,
    "pleos.car.permission.STT_SERVICE",
    "pleos.car.permission.TTS_SERVICE",
)
