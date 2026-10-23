package com.smalltoktok.pleos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smalltoktok.pleos.ui.theme.Palette

// 폰 앱 아이콘(apps/mobile/src/components/TabIcons.tsx, RallyMark.tsx)을 그대로 옮겼다.
// 24 단위 viewBox, 둥근 선 하나(2px)로 그린 SVG path를 PathParser로 읽어 Canvas에 그린다.

private fun path(d: String) = PathParser().parsePathString(d).toPath()

/** 24×24 좌표계로 그린다 */
@Composable
private fun Icon24(size: Dp, draw: DrawScope.() -> Unit) =
    Canvas(Modifier.size(size)) { scale(this.size.width / 24f, pivot = Offset.Zero) { draw() } }

private fun DrawScope.line(d: String, color: Color, dash: PathEffect? = null) = drawPath(
    path(d), color,
    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash),
)

private fun DrawScope.ball(cx: Float, cy: Float, r: Float, stroke: Float) {
    drawCircle(Palette.accent, r, Offset(cx, cy))
    drawCircle(Palette.ink, r, Offset(cx, cy), style = Stroke(stroke))
}

/** 저녁 연습: 랠리 궤적 위의 라임 공 */
@Composable
fun EveningIcon(color: Color, size: Dp = 32.dp) = Icon24(size) {
    line("M3 18 C 6 8, 11 6, 15 11", color, PathEffect.dashPathEffect(floatArrayOf(0.1f, 3.6f)))
    ball(18f, 8f, 3.4f, 2f)
}

/** 아쉬운 대화: 되감기 (그 순간으로 돌아가 다시) */
@Composable
fun ReflectIcon(color: Color, size: Dp = 32.dp) = Icon24(size) {
    line("M4.5 12a7.5 7.5 0 1 0 2.2-5.3", color)
    line("M4 4.5v4h4", color)
    ball(12f, 12f, 2.2f, 1.6f)
}

/** 아침 카드: 떠오르는 해 */
@Composable
fun MorningIcon(color: Color, size: Dp = 32.dp) = Icon24(size) {
    line("M3 18h18", color)
    line("M7 18a5 5 0 0 1 10 0", color)
    line("M12 6v2.5M5.6 9.6l1.7 1.7M18.4 9.6l-1.7 1.7", color)
}

/** 관계 노트: 책갈피 꽂힌 노트 */
@Composable
fun NotesIcon(color: Color, size: Dp = 32.dp) = Icon24(size) {
    drawRoundRect(
        color, Offset(5f, 3.5f), Size(14f, 17f), CornerRadius(2.5f),
        style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    line("M9 9h6M9 13h4", color)
    line("M15 3.5v5l1.6-1.2L18.2 8.5v-5", color)
}

/** 헤더: 알림 (종) */
@Composable
fun BellIcon(color: Color, size: Dp = 28.dp) = Icon24(size) {
    line("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2H4.5z", color)
    line("M10 20.5a2 2 0 0 0 4 0", color)
}

/** 헤더: 설정 (톱니) */
@Composable
fun GearIcon(color: Color, size: Dp = 28.dp) = Icon24(size) {
    drawCircle(color, 3f, Offset(12f, 12f), style = Stroke(2f))
    line("M12 3.5v2.2M12 18.3v2.2M3.5 12h2.2M18.3 12h2.2M6 6l1.6 1.6M16.4 16.4 18 18M6 18l1.6-1.6M16.4 7.6 18 6", color)
}

/**
 * 스몰톡톡 로고 "랠리": 먹색 둥근 타일 위 흰 점선 궤적과 라임 공.
 * 원본 docs/brand/rally-icon.svg (100 단위). 모양을 바꾸지 말고 크기만 정한다.
 */
@Composable
fun RallyMark(size: Dp = 36.dp, tile: Boolean = true) {
    val trail = remember { path("M22 70 Q44 18 68 50") }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 100f, pivot = Offset.Zero) {
            if (tile) drawRoundRect(Palette.ink, size = Size(100f, 100f), cornerRadius = CornerRadius(22f))
            drawPath(
                trail, Palette.bg,
                style = Stroke(6f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 12f))),
            )
            drawCircle(Palette.accent, 10f, Offset(72f, 57f))
        }
    }
}
