package com.smalltoktok.pleos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smalltoktok.pleos.ui.theme.Palette

/**
 * 처음 켤 때: "어떻게 불러드릴까요?" 성·이름 입력. 설정에서도 같은 입력 칸을 쓴다(asSettings).
 * 주행 중에는 키보드 대신 라임 공을 눌러 말로 입력한다(Pleos 가이드: 키보드보다 음성 우선).
 */
@Composable
fun NameScreen(
    vm: ProfileViewModel,
    parked: Boolean,
    asSettings: Boolean = false,
    onNotices: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var family by rememberSaveable(state.family) { mutableStateOf(state.family.orEmpty()) }
    var given by rememberSaveable(state.given) { mutableStateOf(state.given.orEmpty()) }
    var gender by rememberSaveable(state.gender) { mutableStateOf(state.gender) }
    var birthYear by rememberSaveable(state.birthYear) { mutableStateOf(state.birthYear?.toString().orEmpty()) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!asSettings) RallyMark(size = 88.dp)
            Text(if (asSettings) "내 이름" else "어떻게 불러드릴까요?", style = MaterialTheme.typography.headlineMedium)
            Text(
                "대화 상대가 \"현대야\", \"현대 형\"처럼 이름으로 불러 줘요. 나만 볼 수 있어요.",
                style = MaterialTheme.typography.bodyLarge, color = Palette.muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RallyBall(listening = state.listening, animate = parked, onClick = {
                    if (!state.listening) vm.listenName { f, g -> family = f; given = g }
                })
                Spacer(Modifier.width(20.dp))
                Text(
                    if (state.listening) "이름을 말해 주세요" else "공을 누르고 이름을 말해도 돼요",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (parked) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = family, onValueChange = { family = it.take(10) },
                        label = { Text("성") }, singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = given, onValueChange = { given = it.take(20) },
                        label = { Text("이름") }, singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(2f),
                    )
                }
            } else if (family.isNotEmpty() || given.isNotEmpty()) {
                Text("$family$given 님", style = MaterialTheme.typography.headlineMedium)
            } else {
                Text("운전 중에는 말로 입력해 주세요. 글자로 입력하려면 차를 세워 주세요.", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
            }
            // 오빠·누나·형·언니를 고르는 데만 쓴다. 비워 두면 이름만 부른다
            Text("성별과 태어난 해 (선택)", style = MaterialTheme.typography.titleLarge)
            Segmented(listOf("m" to "남자", "f" to "여자"), gender) { gender = if (gender == it) null else it }
            if (parked) {
                OutlinedTextField(
                    value = birthYear, onValueChange = { v -> birthYear = v.filter { it.isDigit() }.take(4) },
                    label = { Text("태어난 해 (예: 1996)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                )
            }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            BigButton(
                if (state.saving) "저장하는 중…" else if (asSettings) "저장하기" else "시작하기",
                accent = true,
            ) {
                if (!state.saving && (family.isNotBlank() || given.isNotBlank())) vm.save(family, given, gender, birthYear, onDone)
            }
            if (!asSettings) {
                OutlinedButton(onClick = { vm.skip() }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text("나중에 할게요", style = MaterialTheme.typography.labelLarge)
                }
            } else if (parked) {
                // 글이 많은 고지 화면은 정차 중에만 연다
                OutlinedButton(onClick = onNotices, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text("오픈소스·저작권 고지", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
