package com.smalltoktok.pleos.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.smalltoktok.pleos.data.Tip
import com.smalltoktok.pleos.ui.theme.Palette

// 스몰토크 잘하는 법 (2026-10-23): 아침 카드 목록에 섞이는 영상 카드·글 카드와, 누르면 열리는 플레이어·읽어주기 화면

/** 영상 카드: 유튜브 썸네일 + 제목 + 채널·길이. 주행 중에는 "차를 세우면 볼 수 있어요" */
@Composable
fun VideoTipCard(tip: Tip, parked: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.surface)
            .then(if (parked) Modifier.clickable(onClick = onOpen) else Modifier),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.ink)) {
            AsyncImage(
                model = tip.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 재생 표시 (라임 공 위 삼각형)
            Box(
                Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Palette.accent.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Palette.onAccent, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            tip.durationSec?.let {
                Text(
                    "%d:%02d".format(it / 60, it % 60),
                    color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Palette.ink.copy(alpha = 0.8f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("스몰토크 잘하는 법 · 영상", style = MaterialTheme.typography.labelLarge, color = Palette.muted)
            Text(tip.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(tip.summary, style = MaterialTheme.typography.bodyLarge, color = Palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (parked) tip.channel.orEmpty() else "차를 세우면 볼 수 있어요",
                style = MaterialTheme.typography.labelLarge, color = if (parked) Palette.muted else Palette.ink,
            )
        }
    }
}

/** 글 카드: 제목 + 한 줄 + "읽어주기 n분". 주행 중에도 열 수 있다(소리로 듣는다) */
@Composable
fun ArticleTipCard(tip: Tip, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.ink).clickable(onClick = onOpen).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("스몰토크 잘하는 법 · 읽어주는 글", style = MaterialTheme.typography.labelLarge, color = Palette.accent)
        Text(tip.title, style = MaterialTheme.typography.titleLarge, color = Palette.surface)
        Text(tip.summary, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFB3B8AE))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.accent), contentAlignment = Alignment.Center) {
                Text("▶", color = Palette.onAccent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(12.dp))
            Text("읽어주기 · 약 ${tip.readMinutes}분", style = MaterialTheme.typography.labelLarge, color = Palette.surface)
        }
    }
}

/**
 * 앱 안 유튜브 플레이어 (공식 임베드). 정차 중에만 열리고, 주행으로 바뀌면 MorningScreen이 닫는다.
 * Pleos 보안 가이드: WebView는 필요한 범위만 — 유튜브 임베드 밖으로 이동하지 않게 막는다.
 * 유튜브 임베드는 출처(Referer)가 없으면 재생을 거부해서, 앱 주소를 기준 URL로 둔 HTML로 띄운다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoPlayer(tip: Tip, onClose: () -> Unit) {
    val id = tip.youtubeId ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tip.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(tip.channel.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.height(64.dp)) { Text("닫기", style = MaterialTheme.typography.labelLarge) }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            val html = """
                <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                <style>html,body{margin:0;height:100%;background:#0E0F0C}iframe{border:0;width:100%;height:100%}</style></head>
                <body><iframe src="https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0&modestbranding=1"
                allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe></body></html>
            """.trimIndent()
            var web: WebView? = null
            // 주의: 여기에 clip(둥근 모서리)을 걸면 WebView의 동영상 면이 합성되지 않아 소리만 나고 화면이 검다 (2026-10-23)
            AndroidView(
                modifier = Modifier.widthIn(max = 1200.dp).fillMaxWidth().aspectRatio(16f / 9f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = false
                        webViewClient = object : WebViewClient() {
                            // 임베드 안의 링크(유튜브로 이동 등)는 따라가지 않는다
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                        }
                        loadDataWithBaseURL("https://smalltoktok.app/", html, "text/html", "utf-8", null)
                        web = this
                    }
                },
            )
            DisposableEffect(Unit) {
                onDispose {
                    web?.apply { loadUrl("about:blank"); stopLoading(); destroy() }
                }
            }
        }
    }
}

/** 읽어주는 글: 제목과 본문, 지금 읽는 문단을 라임으로 표시. 주행 중에는 본문 대신 제목과 읽기 버튼만 */
@Composable
fun ArticleReader(tip: Tip, reading: Int?, parked: Boolean, onRead: () -> Unit, onStop: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("읽어주는 글", style = MaterialTheme.typography.labelLarge, color = Palette.muted, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose, modifier = Modifier.height(64.dp)) { Text("닫기", style = MaterialTheme.typography.labelLarge) }
        }
        Text(tip.title, style = MaterialTheme.typography.headlineMedium)
        BigButton(if (reading != null) "읽기 멈추기" else "▶ 처음부터 읽어주기", accent = reading == null) {
            if (reading != null) onStop() else onRead()
        }
        if (!parked) {
            Text("운전 중에는 소리로 들려드려요.", style = MaterialTheme.typography.bodyLarge, color = Palette.muted)
        } else {
            tip.paragraphs().forEachIndexed { i, p ->
                Text(
                    p,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, lineHeight = 38.sp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (i == reading) Palette.accent.copy(alpha = 0.45f) else Color.Transparent)
                        .padding(12.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
