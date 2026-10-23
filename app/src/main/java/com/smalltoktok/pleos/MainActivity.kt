package com.smalltoktok.pleos

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.smalltoktok.pleos.ui.App
import com.smalltoktok.pleos.ui.TalkViewModel
import com.smalltoktok.pleos.ui.theme.SmallTokTheme

class MainActivity : ComponentActivity() {
    private val vm: TalkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
            .launch(PERMISSIONS)
        setContent { SmallTokTheme { App(vm) } }
    }

    private companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            "pleos.car.permission.STT_SERVICE",
            "pleos.car.permission.TTS_SERVICE",
        )
    }
}
