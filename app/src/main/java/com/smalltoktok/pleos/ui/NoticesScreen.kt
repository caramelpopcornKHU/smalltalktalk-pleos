package com.smalltoktok.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.smalltoktok.pleos.ui.theme.Palette

/** 앱에 들어간 외부 리소스와 라이선스 (docs/credits.md와 맞춘다) */
private data class Notice(val name: String, val use: String, val license: String, val url: String)

private val NOTICES = listOf(
    // Icons8 무료 이용 조건: 앱 안에 "Icons by Icons8"과 링크를 표기한다
    Notice("Icons by Icons8", "AI 판사 탭 저울 아이콘 (Scales)", "Icons8 무료 이용 조건", "https://icons8.com"),
    Notice("Pretendard", "앱 글꼴", "SIL Open Font License 1.1", "https://github.com/orioncactus/pretendard"),
    Notice("Android Jetpack (AndroidX)", "화면·생명주기·내비게이션 (Compose, Activity, Lifecycle, Navigation, AppCompat, Core)", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    Notice("Material Components", "기본 테마", "Apache License 2.0", "https://github.com/material-components/material-components-android"),
    Notice("Kotlin, kotlinx.coroutines", "언어와 비동기 처리", "Apache License 2.0", "https://kotlinlang.org"),
    Notice("OkHttp", "서버 통신", "Apache License 2.0", "https://square.github.io/okhttp/"),
    Notice("Coil", "인물 사진 불러오기", "Apache License 2.0", "https://coil-kt.github.io/coil/"),
    Notice("Pleos SpeechToText, TextToSpeech, NaviHelper SDK", "음성 인식·음성 합성·내비 목적지", "Pleos Playground 이용약관", "https://document.pleos.ai"),
)

/** 문의처 (2026-11-05). 브랜드 계정만 쓰고 실명은 넣지 않는다. README의 "문의"와 맞춘다 */
const val CONTACT_EMAIL = "smalltalktalk.official@gmail.com"
const val CONTACT_INSTAGRAM = "@smalltalktalk.official"

/** 설정·고지 화면 아래의 문의 카드. Pleos에는 메일 앱·브라우저가 없을 수 있어 누르는 링크 없이 글로만 보여 준다 */
@Composable
fun ContactCard() = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.surface).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text("문의", style = MaterialTheme.typography.titleLarge)
    Text("이메일 $CONTACT_EMAIL", style = MaterialTheme.typography.bodyLarge)
    Text("인스타그램 $CONTACT_INSTAGRAM", style = MaterialTheme.typography.bodyLarge)
}

/**
 * 설정 → 오픈소스·저작권 고지. 글이 많은 화면이라 정차 중에만 연다(설정에서 버튼을 정차 중에만 보인다).
 * Pleos에는 브라우저가 없어 링크는 주소만 보여 준다.
 */
@Composable
fun NoticesScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("오픈소스·저작권 고지", style = MaterialTheme.typography.headlineMedium)
            Text(
                "스몰톡톡은 아래 리소스와 오픈소스를 사용해요. Apache License 2.0 전문은 https://www.apache.org/licenses/LICENSE-2.0 에서 볼 수 있어요.",
                style = MaterialTheme.typography.bodyLarge, color = Palette.muted,
            )
            NOTICES.forEach { n ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.surface).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(n.name, style = MaterialTheme.typography.titleLarge)
                    Text(n.use, style = MaterialTheme.typography.bodyLarge)
                    Text("${n.license} · ${n.url}", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
                }
            }
            ContactCard()
        }
    }
}
