package com.smalltoktok.pleos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smalltoktok.pleos.Services
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val loaded: Boolean = false,
    val family: String? = null,
    val given: String? = null,
    /** "나중에 할게요"를 눌렀는지 (이 기기에만 저장) */
    val skipped: Boolean = false,
    val saving: Boolean = false,
    val listening: Boolean = false,
    val error: String? = null,
) {
    /** 헤더 동그라미에 보여 줄 글자 (성 첫 글자) */
    val initial get() = family?.take(1)
    /** 처음 켰을 때 이름 입력 화면을 보여 줄지 */
    val needsName get() = loaded && family == null && given == null && !skipped
}

/** 사용자 성·이름 (2026-10-23). 서버 profile에 저장하고, 헤더와 대화 습관 리포트에서 쓴다 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val services = Services.get(app)
    private val prefs = app.getSharedPreferences("profile", 0)

    private val _state = MutableStateFlow(ProfileState(skipped = prefs.getBoolean("nameSkipped", false)))
    val state: StateFlow<ProfileState> = _state

    init {
        viewModelScope.launch {
            runCatching { services.api.profileName() }
                .onSuccess { (f, g) -> _state.update { it.copy(loaded = true, family = f, given = g) } }
                // 불러오지 못하면 입력 화면으로 막지 않는다
                .onFailure { _state.update { it.copy(loaded = true, skipped = true) } }
        }
    }

    fun save(family: String, given: String, onDone: () -> Unit = {}) {
        val f = family.trim().take(10).ifEmpty { null }
        val g = given.trim().take(20).ifEmpty { null }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { services.api.saveProfileName(f, g) }
                .onSuccess {
                    _state.update { it.copy(saving = false, family = f, given = g) }
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun skip() {
        prefs.edit().putBoolean("nameSkipped", true).apply()
        _state.update { it.copy(skipped = true) }
    }

    /**
     * 이름을 말로 받는다(주행 중에는 키보드 대신). "정형민"처럼 붙여 말하면 첫 글자를 성으로 나눈다.
     * 두 글자 성(남궁 등)은 입력 칸에서 고칠 수 있다.
     */
    fun listenName(onHeard: (family: String, given: String) -> Unit) {
        _state.update { it.copy(listening = true) }
        viewModelScope.launch {
            val heard = services.voice.listen(startTimeoutMs = 8_000, maxSpeechMs = 10_000)
            _state.update { it.copy(listening = false) }
            val name = heard.text.replace(Regex("[^가-힣a-zA-Z ]"), "").replace(" ", "")
                .removeSuffix("입니다").removeSuffix("이에요").removeSuffix("예요")
            if (name.length >= 2) onHeard(name.take(1), name.drop(1))
        }
    }
}
