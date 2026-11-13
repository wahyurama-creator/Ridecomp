package com.ridecomp.app.navigation

import android.content.ContentResolver
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A GPX file opened from another app ("Open with", T3.1, decision D-44). The exported launcher activity
 * accepts only `ACTION_VIEW` with a `content://` URI (SEC-PLT-01, SEC-IN-04); the file itself is parsed
 * as untrusted input by the import use case.
 */
object IncomingGpx {
    private val _uri = MutableStateFlow<String?>(null)
    val uri: StateFlow<String?> = _uri.asStateFlow()

    fun offer(intent: Intent?) {
        val data = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        if (data.scheme == ContentResolver.SCHEME_CONTENT) _uri.value = data.toString()
    }

    /** Called once the routes screen started the import. */
    fun consumed() {
        _uri.value = null
    }
}
