package com.ridecomp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.theme.RideCompTheme

/** Single activity hosting the Compose UI. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RideCompTheme {
                EmptyState(message = stringResource(R.string.app_name))
            }
        }
    }
}
