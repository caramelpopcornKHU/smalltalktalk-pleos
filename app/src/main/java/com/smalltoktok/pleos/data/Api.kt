package com.smalltoktok.pleos.data

import android.content.Context
import com.smalltoktok.pleos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, val code: String, message: String) : Exception(message)

data class Scenario(
    val id: String,
    val title: String,
    val who: String,
    val whoShort: String,
    val place: String,
    val characterId: String?,
    val firstSay: String?,
    /** shared(공유 상황) 또는 내 이야기로 만든 연습 */
    val kind: String = "shared",
) {
    val mine get() = kind != "shared"
}

data class Message(val speaker: String, val text: String) {
    fun toJson() = JSONObject().put("speaker", speaker).put("text", text)
}

/** 한 번의 대답에서 잰 말하기 흐름 (서버 coaching.ts의 DeliveryMetrics와 같다) */
data class Metrics(
    val responseDelayMs: Long,
    val speakingMs: Long,
    val pauseCount: Int = 0,
    val longestPauseMs: Long = 0,
) {
    fun toJson() = JSONObject()
        .put("responseDelayMs", responseDelayMs)
        .put("speakingMs", speakingMs)
        .put("pauseCount", pauseCount)
        .put("longestPauseMs", longestPauseMs)
}

/** audioUrl: 서버(Google Chirp)가 합성한 음성 파일. 합성에 실패하면 null이고 Pleos TTS로 읽는다 */
data class Started(val conversationId: String, val maxPartnerTurns: Int, val firstSay: String, val firstAudioUrl: String?)
data class PartnerTurn(val say: String, val end: Boolean, val audioUrl: String?)

data class ReflectionLine(val line: String, val why: String)
/** 아쉬운 대화 결과. scenario가 null이면 연습으로 만들지 않은 이야기(무거운 이야기 등) */
data class ReflectionResult(
    val reflectionId: String,
    val summary: String,
    val lines: List<ReflectionLine>,
    val scenario: Scenario?,
)

/** 아침 카드. 말투 토글: 동료에게(coworkerLine) / 처음 보는 사람에게(strangerLine) */
data class MorningCard(
    val id: String,
    val category: String,
    val topic: String,
    val coworkerLine: String,
    val strangerLine: String,
    val bridge: String,
    val sourceName: String?,
)
data class Feedback(
    val highlight: String?,
    val highlightReason: String?,
    val missedMoment: String?,
    val suggestion: String?,
    val deliveryFeedback: String?,
    val point: String,
)

/**
 * staging Supabase를 직접 부른다. 폰 앱(supabase-js)이 하는 일 중 필요한 것만:
 * 익명 로그인, 시나리오 목록(RLS), Edge Function `conversation`.
 * 라이브러리를 줄여 메모리(앱 리뷰 기준 400MB)를 아낀다.
 */
class Api(context: Context) {
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_KEY
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
    // TODO: 출시 전 토큰을 Keystore 기반 저장소로 옮긴다 (Pleos 보안 가이드)
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val authLock = Mutex()

    fun characterImageUrl(id: String) = "$base/storage/v1/object/public/characters/$id.jpg"

    /** scenario-audio 버킷 안 경로 → 공개 URL */
    private fun audioUrl(path: String?) = path?.let { "$base/storage/v1/object/public/scenario-audio/$it" }

    private fun scenarioOf(o: JSONObject): Scenario {
        val turns = o.optJSONArray("scenario_turn") ?: JSONArray()
        val first = (0 until turns.length()).map { turns.getJSONObject(it) }.minByOrNull { it.getInt("position") }
        return Scenario(
            id = o.getString("id"),
            title = o.getString("title"),
            who = o.getString("who"),
            whoShort = o.getString("who_short"),
            place = o.getString("place"),
            characterId = o.optStringOrNull("character_id"),
            firstSay = first?.optStringOrNull("say"),
            kind = o.optString("kind", "shared"),
        )
    }

    suspend fun scenarios(): List<Scenario> {
        val arr = JSONArray(call("GET", "/rest/v1/scenario?select=$SCENARIO_COLUMNS&hidden_at=is.null&order=created_at.asc"))
        return (0 until arr.length()).map { scenarioOf(arr.getJSONObject(it)) }
    }

    /** 아쉬운 대화: 털어놓은 이야기로 요약·추천 한마디·연습 상황을 만든다 (Edge Function reflection) */
    suspend fun reflect(story: String, distance: String?): ReflectionResult {
        val o = JSONObject(
            call("POST", "/functions/v1/reflection", JSONObject().put("story", story).put("distance", distance ?: JSONObject.NULL)),
        )
        val arr = o.optJSONArray("lines") ?: JSONArray()
        val scenarioId = o.optStringOrNull("scenarioId")
        val scenario = scenarioId?.let {
            val rows = JSONArray(call("GET", "/rest/v1/scenario?select=$SCENARIO_COLUMNS&id=eq.$it"))
            if (rows.length() > 0) scenarioOf(rows.getJSONObject(0)) else null
        }
        return ReflectionResult(
            reflectionId = o.getString("reflectionId"),
            summary = o.optString("summary"),
            lines = (0 until arr.length()).map { arr.getJSONObject(it) }.map { ReflectionLine(it.getString("line"), it.getString("why")) },
            scenario = scenario,
        )
    }

    /** 추천 한마디 들어보기 음성 (서버 TTS). 실패하면 null */
    suspend fun reflectionLineAudio(reflectionId: String, index: Int): String? = runCatching {
        val o = JSONObject(
            call("POST", "/functions/v1/reflection", JSONObject().put("action", "speak").put("reflectionId", reflectionId).put("index", index)),
        )
        audioUrl(o.optStringOrNull("audioPath"))
    }.getOrNull()

    /** 오늘(KST) 발행된 공유 아침 카드. 날씨가 맨 앞 */
    suspend fun morningCards(): List<MorningCard> {
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString()
        val select = "id,coworker_line,stranger_line,bridge,morning_topic!inner(date,category,topic,source)"
        val arr = JSONArray(call("GET", "/rest/v1/morning_card?select=$select&owner_id=is.null&morning_topic.date=eq.$today"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val t = o.getJSONObject("morning_topic")
            MorningCard(
                id = o.getString("id"),
                category = t.getString("category"),
                topic = t.getString("topic"),
                coworkerLine = o.getString("coworker_line"),
                strangerLine = o.getString("stranger_line"),
                bridge = o.getString("bridge"),
                sourceName = t.optJSONObject("source")?.optStringOrNull("name"),
            )
        }.sortedBy { MORNING_ORDER.indexOf(it.category).let { i -> if (i < 0) 99 else i } }
    }

    suspend fun start(scenarioId: String): Started {
        val o = conversation(JSONObject().put("action", "start").put("scenarioId", scenarioId))
        return Started(
            conversationId = o.getString("conversationId"),
            maxPartnerTurns = o.getInt("maxPartnerTurns"),
            firstSay = o.getJSONObject("first").getString("say"),
            firstAudioUrl = audioUrl(o.getJSONObject("first").optStringOrNull("audioPath")),
        )
    }

    suspend fun turn(conversationId: String, messages: List<Message>): PartnerTurn {
        val o = conversation(
            JSONObject().put("action", "turn").put("conversationId", conversationId)
                .put("messages", JSONArray(messages.map { it.toJson() })),
        )
        return PartnerTurn(say = o.getString("say"), end = o.optBoolean("end"), audioUrl = audioUrl(o.optStringOrNull("audioPath")))
    }

    suspend fun finish(conversationId: String, messages: List<Message>, metrics: List<Metrics>): Feedback {
        val f = conversation(
            JSONObject().put("action", "finish").put("conversationId", conversationId)
                .put("messages", JSONArray(messages.map { it.toJson() }))
                .put("metrics", JSONArray(metrics.map { it.toJson() })),
        ).getJSONObject("feedback")
        return Feedback(
            highlight = f.optStringOrNull("highlight"),
            highlightReason = f.optStringOrNull("highlightReason"),
            missedMoment = f.optStringOrNull("missedMoment"),
            suggestion = f.optStringOrNull("suggestion"),
            deliveryFeedback = f.optStringOrNull("deliveryFeedback"),
            point = f.optString("point"),
        )
    }

    private suspend fun conversation(body: JSONObject) =
        JSONObject(call("POST", "/functions/v1/conversation", body))

    private suspend fun call(method: String, path: String, body: JSONObject? = null): String {
        val token = accessToken()
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(base + path)
                .header("apikey", key)
                .header("Authorization", "Bearer $token")
                .method(method, body?.toString()?.toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw errorOf(res.code, text)
                text
            }
        }
    }

    /** 익명 사용자 토큰. 없으면 만들고, 만료가 가까우면 새로 받는다 */
    private suspend fun accessToken(): String = authLock.withLock {
        val token = prefs.getString("access", null)
        val expiresAt = prefs.getLong("expiresAt", 0)
        if (token != null && System.currentTimeMillis() < expiresAt - 60_000) return token
        val refresh = prefs.getString("refresh", null)
        val session = if (refresh != null) {
            runCatching { auth("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh)) }
                .getOrElse { auth("/auth/v1/signup", JSONObject()) }
        } else {
            auth("/auth/v1/signup", JSONObject())
        }
        prefs.edit()
            .putString("access", session.getString("access_token"))
            .putString("refresh", session.getString("refresh_token"))
            .putLong("expiresAt", System.currentTimeMillis() + session.getLong("expires_in") * 1000)
            .apply()
        session.getString("access_token")
    }

    private suspend fun auth(path: String, body: JSONObject) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(base + path)
            .header("apikey", key)
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw errorOf(res.code, text)
            JSONObject(text)
        }
    }

    private fun errorOf(status: Int, text: String): ApiException {
        val err = runCatching { JSONObject(text).optJSONObject("error") }.getOrNull()
        return ApiException(status, err?.optString("code") ?: "http_$status", err?.optString("message") ?: text.take(200))
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val SCENARIO_COLUMNS = "id,kind,title,who,who_short,place,character_id,scenario_turn(position,say)"
        // 폰 앱 MORNING_CATEGORIES 순서 (packages/shared/src/morning.ts)
        val MORNING_ORDER = listOf("weather", "fun-fact", "season", "food", "outing", "culture", "fuel", "movie-drama", "kpop", "issue", "sports")
    }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).ifEmpty { null }
