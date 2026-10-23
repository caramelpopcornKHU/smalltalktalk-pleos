package com.smalltoktok.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smalltoktok.pleos.ui.theme.Palette

/**
 * 하단 탭. 폰 앱의 5개 탭 중 대화매칭은 공모전 버전에서 뺐다(2026-10-23).
 * 이름은 화면 읽기 프로그램용으로만 붙인다.
 */
enum class Tab(val label: String, val icon: @Composable (Color) -> Unit) {
    Evening("저녁 연습", { EveningIcon(it) }),
    Reflect("아쉬운 대화", { ReflectIcon(it) }),
    Morning("아침 카드", { MorningIcon(it) }),
    Notes("관계 노트", { NotesIcon(it) }),
}

/** 탭 바 밖의 화면 (헤더 버튼으로 연다) */
enum class Extra(val label: String) { Notifications("알림"), Settings("설정") }

// 탭 바는 아이콘 4개가 너무 벌어지지 않게 폭을 제한한다. 헤더는 화면 폭에 맞춰 늘어난다(2026-10-23 피드백)
private val TabBarMaxWidth = 720.dp

/**
 * 탭 화면 머리 (폰 앱 B안, docs/prototypes/header.html): 떠 있는 먹색 캡슐.
 * 왼쪽 로고(타일이 캡슐과 같은 색이라 궤적과 공만 보인다)와 앱 이름, 오른쪽 알림·설정.
 * 차량은 손이 닿기 멀어 버튼을 폰(40dp)보다 크게 둔다.
 */
@Composable
fun CapsuleHeader(
    unread: Boolean,
    onBell: () -> Unit,
    onGear: () -> Unit,
    /** 성 첫 글자 (2026-10-23). 이름이 없으면 동그라미를 그리지 않는다 */
    initial: String? = null,
    onAvatar: () -> Unit = onGear,
) {
    // 좌우 여백은 아래 화면 내용(32dp)과 맞춘다
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(CircleShape)
                .background(Palette.ink)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RallyMark(size = 44.dp)
            Spacer(Modifier.size(8.dp))
            Text("스몰톡톡", color = Palette.surface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            CircleButton(if (unread) "알림, 새 소식 있음" else "알림", onBell) {
                BellIcon(Palette.bg)
                if (unread) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).size(12.dp)
                            .clip(CircleShape).background(Palette.ink).padding(2.dp).clip(CircleShape).background(Palette.accent),
                    )
                }
            }
            CircleButton("설정", onGear) { GearIcon(Palette.bg) }
            // 내 이름 동그라미: 라임 바탕에 성 한 글자. 누르면 이름을 고치는 설정으로
            initial?.let { ch ->
                Spacer(Modifier.size(6.dp))
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Palette.accent)
                        .semantics { contentDescription = "내 이름 설정" }
                        .clickable(role = Role.Button, onClick = onAvatar),
                    contentAlignment = Alignment.Center,
                ) { Text(ch, color = Palette.onAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

/**
 * 떠 있는 먹색 탭 바 (폰 앱 C안, docs/prototypes/tab-bar.html).
 * 글자 대신 아이콘만, 선택된 탭은 흰 아이콘 + 아래 라임 점, 나머지는 흐리게.
 */
@Composable
fun FloatingTabBar(current: Tab?, onSelect: (Tab) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = TabBarMaxWidth)
                .fillMaxWidth()
                .height(84.dp)
                .clip(CircleShape)
                .background(Palette.ink),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val focused = tab == current
                Column(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .semantics {
                            contentDescription = tab.label
                            selected = focused
                        }
                        .clickable(role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.alpha(if (focused) 1f else 0.55f)) { tab.icon(Palette.surface) }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (focused) Palette.accent else Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun CircleButton(label: String, onClick: () -> Unit, size: Dp = 56.dp, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) =
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )

/** 아직 차량 버전으로 옮기지 않은 화면 */
@Composable
fun ComingSoon(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
    }
}
