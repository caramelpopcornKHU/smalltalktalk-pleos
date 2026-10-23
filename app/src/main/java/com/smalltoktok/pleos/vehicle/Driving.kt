package com.smalltoktok.pleos.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 주행 중인지 정차 중인지 (Pleos Vehicle SDK 기어 상태).
 * P단일 때만 정차로 본다. 기어를 읽지 못하면 안전하게 주행 중으로 둔다.
 * 주행 중에는 음성 대화와 최소 화면만, 코칭처럼 글이 많은 화면은 정차 중에만 연다.
 */
class Driving(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private val _parked = MutableStateFlow(false)
    val parked: StateFlow<Boolean> = _parked

    private val _gear = MutableStateFlow<VehicleGear?>(null)
    val gear: StateFlow<VehicleGear?> = _gear

    private val listener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = update(gear)
        override fun onFailed(e: Exception) = Unit
    }

    /** 서비스 연결이 늦을 수 있어 처음 몇 번은 다시 시도한다 */
    fun start(scope: CoroutineScope) {
        runCatching { vehicle.initialize() }
        scope.launch {
            repeat(10) {
                val ok = runCatching {
                    vehicle.getGear().getCurrentGearSelectionState({ update(it) }, { })
                    vehicle.getGear().registerGearSelection(listener)
                }.isSuccess
                if (ok) return@launch
                delay(500)
            }
        }
    }

    private fun update(gear: VehicleGear?) {
        _gear.value = gear
        _parked.value = gear == VehicleGear.GEAR_PARK
    }

    fun release() {
        runCatching { vehicle.getGear().unregisterGearSelection(listener) }
        runCatching { vehicle.release() }
    }
}
