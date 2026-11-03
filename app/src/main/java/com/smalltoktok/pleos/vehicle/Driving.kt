package com.smalltoktok.pleos.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 주행 규제 상태 (AAOS CarUxRestrictions, 2026-10-23 Pleos 가이드 점검으로 교체).
 * 처음에는 Pleos Vehicle SDK로 기어를 읽어 P단만 정차로 봤는데, 가이드는
 * "주행 제한은 플랫폼이 주는 CarUxRestrictions 기준, 기어 같은 표준 데이터는 AAOS Car API"를 요구하고
 * 차량 퍼미션(CAR_DRIVE)은 App Market 사전 승인이 필요해 바꿨다. 지역별 규제도 플랫폼이 반영해 내려준다.
 *
 * parked = 주행 최적화(DO)가 필요 없는 상태. 이때만 글이 많은 화면(코칭, 목록, 키보드)을 연다.
 * 매니페스트에서 MainActivity를 distractionOptimized로 선언해 주행 중에도 앱이 막히지 않는다.
 */
class Driving(context: Context) {
    private val app = context.applicationContext
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    // 차량이 아닌 기기(폰·일반 에뮬레이터)에서는 늘 정차로 본다
    private val _parked = MutableStateFlow(true)
    val parked: StateFlow<Boolean> = _parked

    private val _noKeyboard = MutableStateFlow(false)
    /** 주행 중 키보드 금지 (지역 규제에 따라 플랫폼이 정한다) */
    val noKeyboard: StateFlow<Boolean> = _noKeyboard

    fun start() {
        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return
        // 아직 정보를 받기 전에는 안전하게 주행 중으로 둔다
        _parked.value = false
        runCatching {
            car = Car.createCar(app, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER) { c, ready ->
                if (!ready) {
                    // 차량 서비스가 끊기면 다시 연결될 때까지 안전하게 주행 중으로 본다 (2026-11-03 코드 리뷰)
                    manager = null
                    _parked.value = false
                    return@createCar
                }
                val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return@createCar
                manager = m
                update(m.currentCarUxRestrictions)
                m.registerListener { update(it) }
            }
        }.onFailure {
            // Car 서비스를 쓸 수 없는 환경이면 막지 않는다
            _parked.value = true
        }
    }

    private fun update(r: CarUxRestrictions?) {
        if (r == null) return
        _parked.value = !r.isRequiresDistractionOptimization
        _noKeyboard.value = r.isRequiresDistractionOptimization &&
            (r.activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD) != 0
    }

    fun release() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
    }
}
