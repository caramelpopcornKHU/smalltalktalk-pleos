package com.smalltoktok.pleos.vehicle

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.location.Location
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 즐겨찾기 회사로 가는 길(출근), 집으로 가는 길(퇴근), 그 밖의 곳 */
enum class TripKind { ToOffice, ToHome, Other }

/** 지금 안내 중인 목적지. remainMin은 도착까지 남은 분(모르면 null) */
data class Trip(val name: String, val address: String, val kind: TripKind, val remainMin: Int?)

/**
 * Pleos 내비게이션의 목적지 (2026-11-02, A안 "차의 맥락 쓰기").
 * NaviHelper SDK로 목적지 이름·주소·남은 시간을 읽고, 즐겨찾기 집·회사 좌표와 비교해 출근·퇴근을 가른다.
 * 목적지는 앱 안에서 상황 고르기에만 쓰고 서버로 보내지 않는다.
 * 내비 앱이 없는 기기(폰·일반 에뮬레이터)에서는 initialize가 실패하고 trip은 계속 null이다.
 */
class Navi(context: Context) : NaviHelperEventListener {
    private val app = context.applicationContext
    private var helper: NaviHelper? = null
    private var bookmarks: List<BookmarkInfo> = emptyList()

    private val _trip = MutableStateFlow<Trip?>(null)
    val trip: StateFlow<Trip?> = _trip

    fun start() {
        runCatching {
            NaviHelper(app).also {
                it.initialize()
                it.addListener(this)
                helper = it
                it.getBookmarkInfo()
                it.getDestinationInfo()
            }
        }.onFailure {
            Log.w(TAG, "NaviHelper 연결 실패", it)
            helper = null
        }
    }

    fun release() {
        runCatching {
            helper?.removeListener(this)
            helper?.release()
        }
        helper = null
    }

    private fun refresh() {
        runCatching { helper?.getDestinationInfo() }
    }

    private fun refreshBookmarks() {
        runCatching { helper?.getBookmarkInfo() }
    }

    override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {
        bookmarks = bookmarkInfoList
        // 즐겨찾기가 목적지보다 늦게 오면 출근·퇴근을 다시 가른다
        refresh()
    }

    override fun onDestinationInfo(destinationInfo: DestinationInfo) {
        val d = destinationInfo
        if (d.name.isNullOrBlank() && d.address.isNullOrBlank()) {
            _trip.value = null
            return
        }
        _trip.value = Trip(
            name = d.name.orEmpty(),
            address = d.address.orEmpty(),
            kind = kindOf(d.latitude, d.longitude),
            remainMin = minutes(d.remainTime),
        )
    }

    /** 주행 중 남은 시간이 바뀔 때 */
    override fun onDrivingInfo(drivingInfo: DrivingInfo) {
        val sec = drivingInfo.destination?.duration ?: return
        _trip.update { it?.copy(remainMin = minutes(sec)) }
    }

    // 안내를 시작할 때마다 즐겨찾기도 다시 받는다 (앱을 켠 뒤 집·회사를 등록했을 수 있다).
    // 즐겨찾기가 오면 onBookmarkInfo가 목적지를 다시 읽어 출근·퇴근을 가른다
    override fun onRouteStarted(routeStartInfo: RouteStartInfo) = refreshBookmarks()
    override fun onBookMarkChanged() = refreshBookmarks()
    override fun onDestinationChanged() = refresh()
    override fun onRouteCancelled() {
        _trip.value = null
    }
    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
        _trip.value = null
    }

    /** 즐겨찾기 집·회사에서 300m 안이면 그곳으로 가는 길로 본다 */
    private fun kindOf(lat: Double, lon: Double): TripKind {
        fun near(type: BookmarkType) = bookmarks.any { b ->
            b.category == type && FloatArray(1).also { Location.distanceBetween(lat, lon, b.latitude, b.longitude, it) }[0] < NEAR_M
        }
        return when {
            near(BookmarkType.OFFICE) -> TripKind.ToOffice
            near(BookmarkType.HOME) -> TripKind.ToHome
            else -> TripKind.Other
        }
    }

    // 남은 시간은 초 단위 (0이면 아직 경로 계산 전)
    private fun minutes(sec: Int) = if (sec > 0) (sec + 59) / 60 else null

    private companion object {
        const val TAG = "Navi"
        const val NEAR_M = 300f
    }
}
