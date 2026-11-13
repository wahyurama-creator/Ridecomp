package com.ridecomp.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ridecomp.app.navigation.IncomingGpx
import com.ridecomp.ui.theme.RideCompTheme

/** Single activity hosting the Compose UI. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) IncomingGpx.offer(intent)
        setContent {
            RideCompTheme {
                RideCompApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        IncomingGpx.offer(intent)
    }
}
