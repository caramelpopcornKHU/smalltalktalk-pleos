package com.smalltoktok.pleos.data

import android.content.SharedPreferences

/** 말동무 대화 스타일 (2026-11-02). key는 서버 BUDDY_STYLES와 같다 */
enum class BuddyStyle(val key: String, val label: String, val description: String) {
    Chatty("chatty", "수다쟁이 친구", "먼저 이야깃거리를 꺼내요. 짧게 답해도 괜찮아요"),
    Listener("listener", "다정한 리스너", "내 얘기를 잘 들어 주고 공감해 줘요"),
    Playful("playful", "장난꾸러기", "티키타카와 가벼운 농담을 좋아해요"),
    Calm("calm", "차분한 선배", "담백하고 조곤조곤, 서두르지 않아요"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * 말동무 설정: 사진(인물)·대화 스타일·말투. 이 기기에만 둔다(난이도·말투 스위치처럼).
 * 성별은 고른 인물에서 나온다(목소리도 인물 성별을 따른다).
 * persona: 말동무의 일상 프로필(하는 일·취미·요즘 빠진 것). 처음 수다를 떨 때 서버가 짓고, 다음부터 그대로 보내
 * 같은 사람으로 이어지게 한다(2026-11-02). 사진이나 스타일을 바꾸면 새 사람이라 다시 짓는다.
 */
data class BuddyConfig(val characterId: String, val style: BuddyStyle, val casual: Boolean, val persona: String? = null) {
    val gender get() = characterId.first().toString()

    fun save(prefs: SharedPreferences) = prefs.edit()
        .putString(KEY_CHARACTER, characterId)
        .putString(KEY_STYLE, style.key)
        .putBoolean(KEY_CASUAL, casual)
        .putString(KEY_PERSONA, persona)
        .apply()

    companion object {
        private const val KEY_CHARACTER = "buddy_character"
        private const val KEY_STYLE = "buddy_style"
        private const val KEY_CASUAL = "buddy_casual"
        private const val KEY_PERSONA = "buddy_persona"

        /** 아직 만들지 않았으면 null (홈에 회색 빈 카드) */
        fun load(prefs: SharedPreferences): BuddyConfig? {
            val id = prefs.getString(KEY_CHARACTER, null) ?: return null
            val style = BuddyStyle.of(prefs.getString(KEY_STYLE, null)) ?: return null
            return BuddyConfig(id, style, prefs.getBoolean(KEY_CASUAL, true), prefs.getString(KEY_PERSONA, null))
        }
    }
}
