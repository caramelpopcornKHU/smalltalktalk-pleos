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
    /** 관계 거리 (first-meet / familiar-face / daily) */
    val distance: String? = null,
    /** 반말 연습을 골랐을 때의 첫 마디 (공유 상황만, 없으면 firstSay) */
    val casualSay: String? = null,
    /** 상황의 원래 말투 (formal / casual). 아쉬운 대화로 만든 연습은 모델이 관계를 보고 정했다 */
    val speechLevel: String = "formal",
) {
    val mine get() = kind != "shared"

    /**
     * 이 상황을 반말로 하는지. 공유 상황은 사용자가 고른 말투(존댓말 | 반말 스위치)를 따르고,
     * 내 이야기로 만든 연습은 원래 정해진 말투를 따른다.
     */
    fun casual(chosen: Boolean) = if (mine) speechLevel == "casual" else chosen

    /** 미리 보기용 첫 마디. 반말이면 반말 첫 마디 */
    fun firstSay(casual: Boolean) = if (casual) casualSay ?: firstSay else firstSay
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
/** 내 이름과 호칭에 쓰는 성별(m/f)·출생연도. 대화 상대가 "현대야", "현대 형"처럼 부른다 (2026-11-03) */
data class ProfileName(
    val family: String? = null,
    val given: String? = null,
    val gender: String? = null,
    val birthYear: Int? = null,
)
/** 말동무 첫 마디 + 서버가 지은(또는 그대로 돌려준) 말동무 프로필 */
data class BuddyStarted(val started: Started, val persona: String?)
data class PartnerTurn(val say: String, val end: Boolean, val audioUrl: String?)

/** 연습 상대 인물 풀 (말동무 사진 고르기). gender: f / m */
data class Character(val id: String, val gender: String, val ageBand: String)

data class ReflectionLine(val line: String, val why: String)
/** 아쉬운 대화 결과. scenario가 null이면 연습으로 만들지 않은 이야기(무거운 이야기 등) */
data class ReflectionResult(
    val reflectionId: String,
    val summary: String,
    val lines: List<ReflectionLine>,
    val scenario: Scenario?,
    /** 털어놓은 원문 (본인만, "고쳐서 다시 만들기"에 씀) */
    val inputText: String = "",
    val createdAt: java.time.Instant? = null,
)

/**
 * 스몰토크 잘하는 법 카드 (DB tip, smalltalktalk content/tips/). 아침 카드 목록에 섞인다.
 * video: 사람이 고른 유튜브 영상(앱 안 플레이어), article: 직접 쓴 글(소리로 읽어 준다)
 */
data class Tip(
    val id: String,
    val kind: String,
    val title: String,
    val summary: String,
    val youtubeId: String?,
    val channel: String?,
    val durationSec: Int?,
    val body: String?,
) {
    val isVideo get() = kind == "video"
    val thumbnailUrl get() = youtubeId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    /** 글을 읽는 데 걸리는 대략의 분 (1분에 300자 남짓) */
    val readMinutes get() = ((body?.length ?: 0) / 300).coerceAtLeast(1)
}

/** 대화 하나의 습관 요약 (smalltalktalk packages/shared/src/habits.ts의 Habits와 같다) */
data class HabitRecord(
    val startedAt: java.time.Instant,
    val distance: String?,
    /** 상대 난이도 easy / normal / hard (이 컬럼 전 대화는 null) */
    val difficulty: String?,
    val answers: List<HabitAnswer>,
    val phrases: List<String>,
    val longestRally: Int,
)

/** 습관 태그 v2 (2026-10-26): criteria는 기준 → hit / miss / na, slips는 편안함을 깬 실수 */
data class HabitAnswer(
    val shape: String,
    val criteria: Map<String, String>,
    val slips: List<String>,
    val context: String,
    val delayMs: Long?,
)

/** AI 판사 판결: winner a / b / none(판결하지 않는 주제), 서버가 합성한 판사 목소리 */
data class Verdict(val winner: String, val title: String, val reason: String, val audioUrl: String?)

/** 팩트폭행 읽기: 반말 친구가 짚어 주는 말과 서버가 합성한 음성 */
data class Roast(val text: String, val audioUrl: String?)

/** 아침 카드. 말투 토글: 동료에게(coworkerLine) / 처음 보는 사람에게(strangerLine) */
data class MorningCard(
    val id: String,
    val category: String,
    val topic: String,
    val coworkerLine: String,
    val strangerLine: String,
    /** 상대가 짧게 받을 때 붙이는 내 디테일 */
    val bridge: String,
    val sourceName: String?,
    /** 소재 명사구 (2026-10-26 형식, 그 전 카드는 null) */
    val hook: String? = null,
    /** 상대가 이야기를 꺼낼 때의 점프 포인트 질문 */
    val ifOpen: String? = null,
    /** 그거 아세요?만 (2026-11-02): 왜 그런지 한 줄, 이럴 때 꺼내 보세요 */
    val why: String? = null,
    val whenToUse: String? = null,
) {
    /** 신기한 지식 카드: 던지는 말이 아니라 "알아 두면 써먹는 지식"으로 보여주고 읽는다 */
    val isFact get() = category == "fun-fact" && whenToUse != null

    /** 카드 앞면·브리핑에 쓰는 소재. 예전 카드는 사실 요약(topic)으로 */
    val shownHook get() = hook ?: topic
}
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
        // 긴 대화의 코칭(습관 태그 포함)은 40초를 넘길 수 있다 (2026-11-03 코드 리뷰)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    /** 토큰은 Keystore로 암호화해 둔다 (Pleos 보안 가이드). uid·만료 시각은 민감정보가 아니라 그대로 */
    private val tokens = SecureStore(prefs)
    private val cache = context.getSharedPreferences("cache", Context.MODE_PRIVATE)
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
            distance = o.optStringOrNull("distance"),
            casualSay = o.optStringOrNull("casual_say"),
            speechLevel = o.optString("speech_level", "formal"),
        )
    }

    suspend fun scenarios(): List<Scenario> {
        val arr = JSONArray(cachedGet("/rest/v1/scenario?select=$SCENARIO_COLUMNS&hidden_at=is.null&order=created_at.asc", "scenarios"))
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
            inputText = story,
        )
    }

    /** 스몰토크 잘하는 법 영상·글 (공개된 것만, 정렬 순). 네트워크가 끊기면 마지막으로 받은 목록 */
    suspend fun tips(): List<Tip> {
        val arr = JSONArray(cachedGet("/rest/v1/tip?select=id,kind,title,summary,youtube_id,channel,duration_sec,body&order=sort.asc", "tips"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Tip(
                id = o.getString("id"),
                kind = o.getString("kind"),
                title = o.getString("title"),
                summary = o.getString("summary"),
                youtubeId = o.optStringOrNull("youtube_id"),
                channel = o.optStringOrNull("channel"),
                durationSec = if (o.isNull("duration_sec")) null else o.optInt("duration_sec"),
                body = o.optStringOrNull("body"),
            )
        }
    }

    /** 지난 아쉬운 대화 (최근 것부터, RLS로 본인 것만) */
    suspend fun reflections(): List<ReflectionResult> {
        val select = "id,summary,lines,scenario_id,input_text,created_at,scenario($SCENARIO_COLUMNS)"
        val arr = JSONArray(call("GET", "/rest/v1/reflection?select=$select&status=eq.done&order=created_at.desc"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val lines = o.optJSONArray("lines") ?: JSONArray()
            ReflectionResult(
                reflectionId = o.getString("id"),
                summary = o.optString("summary"),
                lines = (0 until lines.length()).map { lines.getJSONObject(it) }
                    .map { ReflectionLine(it.optString("line"), it.optString("why")) },
                scenario = o.optJSONObject("scenario")?.let { scenarioOf(it) },
                inputText = o.optString("input_text"),
                createdAt = runCatching { java.time.OffsetDateTime.parse(o.getString("created_at")).toInstant() }.getOrNull(),
            )
        }
    }

    /** 지우기: 털어놓은 원문과 그 이야기로 만든 연습을 함께 지운다 (폰 앱과 같다) */
    suspend fun deleteReflection(r: ReflectionResult) {
        r.scenario?.let { call("DELETE", "/rest/v1/scenario?id=eq." + it.id) }
        call("DELETE", "/rest/v1/reflection?id=eq." + r.reflectionId)
    }

    /** 추천 한마디 들어보기 음성 (서버 TTS). 실패하면 null */
    suspend fun reflectionLineAudio(reflectionId: String, index: Int): String? = runCatching {
        val o = JSONObject(
            call("POST", "/functions/v1/reflection", JSONObject().put("action", "speak").put("reflectionId", reflectionId).put("index", index)),
        )
        audioUrl(o.optStringOrNull("audioPath"))
    }.getOrNull()

    /**
     * 나의 대화 습관 재료: 최근 days일 동안 끝난 자유 대화의 요약 태그 (본인 것만, RLS).
     * 서버가 대화 끝날 때 저장한다(conversation.habits, habit_phrases). 원문은 없다.
     */
    suspend fun habitRecords(days: Int): List<HabitRecord> {
        val since = java.time.Instant.now().minus(java.time.Duration.ofDays(days.toLong())).toString()
        val select = "started_at,difficulty,habits,habit_phrases,scenario(distance)"
        // v2 기준(2026-10-26)으로 태그한 대화만 센다
        val arr = JSONArray(
            call("GET", "/rest/v1/conversation?select=$select&habit_version=eq.2&started_at=gte.$since&order=started_at.desc"),
        )
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val h = o.getJSONObject("habits")
            val answers = h.optJSONArray("answers") ?: JSONArray()
            val phrases = o.optJSONArray("habit_phrases")
            HabitRecord(
                // PostgREST는 "2026-10-23T05:34:06.12+00:00"처럼 오프셋을 붙여 준다
                startedAt = java.time.OffsetDateTime.parse(o.getString("started_at")).toInstant(),
                distance = o.optJSONObject("scenario")?.optStringOrNull("distance"),
                difficulty = o.optStringOrNull("difficulty"),
                answers = (0 until answers.length()).map { answers.getJSONObject(it) }.map { a ->
                    val crit = a.optJSONObject("criteria") ?: JSONObject()
                    val slips = a.optJSONArray("slips") ?: JSONArray()
                    HabitAnswer(
                        shape = a.optString("shape"),
                        criteria = crit.keys().asSequence().associateWith { crit.optString(it) },
                        slips = (0 until slips.length()).map { slips.getString(it) },
                        context = a.optString("context"),
                        delayMs = if (a.isNull("delayMs")) null else a.optLong("delayMs"),
                    )
                },
                phrases = phrases?.let { p -> (0 until p.length()).map { p.getString(it) } } ?: emptyList(),
                longestRally = h.optInt("longestRally"),
            )
        }
    }

    /** 최근 7일 습관을 반말 친구 말투로 짚어 준다 (서버가 기록을 직접 모은다, 대화 3번 미만이면 오류) */
    suspend fun roast(): Roast {
        val o = conversation(JSONObject().put("action", "roast"))
        return Roast(o.getString("text"), audioUrl(o.optStringOrNull("audioPath")))
    }

    /** AI 판사: 논쟁 주제와 A·B 주장으로 판결 (재심이면 이전 판결과 반박을 같이). 내용은 서버에 저장하지 않는다 */
    suspend fun judge(topic: String, a: String, b: String, previous: String?, appealA: String?, appealB: String?): Verdict {
        val body = JSONObject().put("topic", topic).put("a", a).put("b", b)
        if (previous != null) {
            body.put("appeal", JSONObject().put("previous", previous).apply {
                appealA?.let { put("a", it) }
                appealB?.let { put("b", it) }
            })
        }
        val o = JSONObject(call("POST", "/functions/v1/judge", body))
        return Verdict(o.getString("winner"), o.getString("title"), o.getString("reason"), audioUrl(o.optStringOrNull("audioPath")))
    }

    /** 내 성·이름·성별·출생연도 (profile, RLS로 본인 행만). 아직 입력하지 않았으면 모두 null */
    suspend fun profileName(): ProfileName {
        val arr = JSONArray(call("GET", "/rest/v1/profile?select=family_name,given_name,gender,birth_year"))
        if (arr.length() == 0) return ProfileName()
        val o = arr.getJSONObject(0)
        return ProfileName(
            o.optStringOrNull("family_name"), o.optStringOrNull("given_name"), o.optStringOrNull("gender"),
            if (o.isNull("birth_year")) null else o.optInt("birth_year"),
        )
    }

    suspend fun saveProfileName(name: ProfileName) {
        accessToken()
        val uid = prefs.getString("uid", null) ?: throw ApiException(401, "no_user", "user id unknown")
        call(
            "PATCH", "/rest/v1/profile?id=eq." + uid,
            JSONObject().put("family_name", name.family ?: JSONObject.NULL).put("given_name", name.given ?: JSONObject.NULL)
                .put("gender", name.gender ?: JSONObject.NULL).put("birth_year", name.birthYear ?: JSONObject.NULL),
        )
    }

    suspend fun morningCards(): List<MorningCard> {
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString()
        val select = "id,coworker_line,stranger_line,bridge,hook,if_open,why,when_to_use,morning_topic!inner(date,category,topic,source)"
        val arr = JSONArray(cachedGet("/rest/v1/morning_card?select=$select&owner_id=is.null&morning_topic.date=eq.$today", "morning:$today"))
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
                hook = o.optStringOrNull("hook"),
                ifOpen = o.optStringOrNull("if_open"),
                why = o.optStringOrNull("why"),
                whenToUse = o.optStringOrNull("when_to_use"),
            )
        }.sortedBy { MORNING_ORDER.indexOf(it.category).let { i -> if (i < 0) 99 else i } }
    }

    /**
     * casual이면 반말 연습 (서버가 상대·코칭을 반말로). 아니면 상황의 원래 말투.
     * difficulty: 상대 난이도 easy(하) / normal(중) / hard(상)
     */
    suspend fun start(scenarioId: String, casual: Boolean, difficulty: String): Started {
        val body = JSONObject().put("action", "start").put("scenarioId", scenarioId).put("difficulty", difficulty)
        if (casual) body.put("speechLevel", "casual")
        val o = conversation(body)
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

    /** 인물 40명 (말동무 사진 고르기). 네트워크가 끊기면 마지막으로 받은 목록 */
    suspend fun characters(): List<Character> {
        val arr = JSONArray(cachedGet("/rest/v1/character?select=id,gender,age_band&active=is.true&order=id.asc", "characters"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { Character(it.getString("id"), it.getString("gender"), it.getString("age_band")) }
    }

    /**
     * 말동무 첫 마디 (Edge Function buddy). conversationId 자리에 말동무 대화 id를 담는다.
     * persona: 기기에 저장해 둔 말동무 프로필(없으면 서버가 지어 돌려준다). trip: office / home (출퇴근길일 때만)
     */
    suspend fun buddyStart(characterId: String, style: String, casual: Boolean, persona: String?, trip: String?): BuddyStarted {
        val body = JSONObject().put("action", "start").put("characterId", characterId).put("style", style)
            .put("speechLevel", if (casual) "casual" else "formal")
        persona?.let { body.put("persona", it) }
        trip?.let { body.put("trip", it) }
        val o = buddy(body)
        return BuddyStarted(
            Started(
                conversationId = o.getString("chatId"),
                maxPartnerTurns = o.getInt("maxPartnerTurns"),
                firstSay = o.getJSONObject("first").getString("say"),
                firstAudioUrl = audioUrl(o.getJSONObject("first").optStringOrNull("audioPath")),
            ),
            persona = o.optStringOrNull("persona"),
        )
    }

    suspend fun buddyTurn(chatId: String, messages: List<Message>): PartnerTurn {
        val o = buddy(JSONObject().put("action", "turn").put("chatId", chatId).put("messages", JSONArray(messages.map { it.toJson() })))
        return PartnerTurn(say = o.getString("say"), end = o.optBoolean("end"), audioUrl = audioUrl(o.optStringOrNull("audioPath")))
    }

    private suspend fun buddy(body: JSONObject) = JSONObject(call("POST", "/functions/v1/buddy", body))

    /**
     * 주행 중 네트워크가 끊겨도 보이도록 마지막으로 받은 목록을 기기에 둔다 (Pleos 하드웨어 접근 정책: 캐싱·오프라인).
     * 받기에 실패하면(네트워크 오류) 저장해 둔 값을 돌려주고, 저장된 것도 없으면 오류를 그대로 낸다.
     */
    private suspend fun cachedGet(path: String, cacheKey: String): String = try {
        call("GET", path).also { cache.edit().putString(cacheKey, it).apply() }
    } catch (e: java.io.IOException) {
        cache.getString(cacheKey, null) ?: throw e
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): String = try {
        callOnce(method, path, body)
    } catch (e: java.io.IOException) {
        // 터널 같은 데서 잠깐 끊긴 경우를 위해 읽기 요청만 한 번 더 (쓰기는 중복될 수 있어 다시 보내지 않는다)
        if (method != "GET") throw e
        kotlinx.coroutines.delay(1_000)
        callOnce(method, path, body)
    }

    private suspend fun callOnce(method: String, path: String, body: JSONObject? = null): String {
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
        val token = tokens.getString("access")
        val expiresAt = prefs.getLong("expiresAt", 0)
        // uid는 2026-10-23부터 저장한다. 예전 설치는 한 번 새로 받아 uid를 채운다
        if (token != null && prefs.contains("uid") && System.currentTimeMillis() < expiresAt - 60_000) return token
        val refresh = tokens.getString("refresh")
        if (refresh == null && tokens.has("refresh")) {
            // 저장된 토큰을 읽지 못하면(Keystore 키가 바뀜) 이어 쓸 방법이 없어 새 사용자가 된다.
            // 이전 사용자 id를 남겨 두어 나중에 기록을 찾아 옮길 수 있게 한다 (2026-11-03 코드 리뷰)
            prefs.getString("uid", null)?.let { prefs.edit().putString("previousUid", it).apply() }
        }
        val session = if (refresh != null) {
            runCatching { auth("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh)) }
                .getOrElse { e ->
                    // refresh 토큰이 무효(400·401·403)일 때만 새 사용자로 바꾼다. 네트워크 오류나 서버 장애(5xx·429)면
                    // 새 사용자를 만들지 않고 실패시킨다(이름·습관 기록이 사라지므로, 2026-11-03 코드 리뷰)
                    if (e !is ApiException || e.status !in setOf(400, 401, 403)) throw e
                    auth("/auth/v1/signup", JSONObject())
                }
        } else {
            auth("/auth/v1/signup", JSONObject())
        }
        tokens.putString("access", session.getString("access_token"))
        tokens.putString("refresh", session.getString("refresh_token"))
        prefs.edit()
            .putString("uid", session.getJSONObject("user").getString("id"))
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
        const val SCENARIO_COLUMNS = "id,kind,distance,title,who,who_short,place,character_id,casual_say,speech_level,scenario_turn(position,say)"
        // 폰 앱 MORNING_CATEGORIES 순서 (packages/shared/src/morning.ts)
        val MORNING_ORDER = listOf("weather", "fun-fact", "season", "food", "outing", "culture", "fuel", "movie-drama", "kpop", "issue", "sports")
    }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).ifEmpty { null }
