package com.smalltoktok.pleos

import android.content.Context
import com.smalltoktok.pleos.data.Api
import com.smalltoktok.pleos.vehicle.Driving
import com.smalltoktok.pleos.voice.Voice

/**
 * 앱 전체가 함께 쓰는 것들. Pleos 음성·차량 서비스 연결은 하나만 두고 탭(ViewModel)들이 나눠 쓴다.
 * 프로세스가 살아 있는 동안 유지한다(차량 앱은 화면에서 사라져도 백그라운드에 남는 일이 잦다).
 */
class Services private constructor(context: Context) {
    val api = Api(context)
    val voice = Voice(context).also { it.initialize() }
    val driving = Driving(context).also { it.start() }

    companion object {
        @Volatile private var instance: Services? = null

        fun get(context: Context): Services =
            instance ?: synchronized(this) {
                instance ?: Services(context.applicationContext).also { instance = it }
            }
    }
}
