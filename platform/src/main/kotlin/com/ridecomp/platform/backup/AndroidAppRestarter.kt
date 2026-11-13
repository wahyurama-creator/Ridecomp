package com.ridecomp.platform.backup

import android.content.Context
import android.content.Intent
import com.ridecomp.core.source.AppRestarter
import kotlin.system.exitProcess

/**
 * Restarts the app after a restore so the staged database is swapped in before anything opens it
 * (decision D-39). Called only from the restore screen, never during a ride.
 */
class AndroidAppRestarter(private val context: Context) : AppRestarter {
    override fun restart() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        exitProcess(0)
    }
}
