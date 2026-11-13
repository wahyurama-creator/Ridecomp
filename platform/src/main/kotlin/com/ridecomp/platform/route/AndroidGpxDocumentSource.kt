package com.ridecomp.platform.route

import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.ridecomp.core.source.GpxDocumentSource
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * GPX documents from the SAF picker or "Open with". Only `content://` is accepted: no file paths from
 * outside (SEC-IN-04); the display name is a label, never a path.
 */
class AndroidGpxDocumentSource(private val context: Context) : GpxDocumentSource {
    override fun open(uri: String): InputStream {
        val parsed = uri.toUri()
        if (parsed.scheme != ContentResolver.SCHEME_CONTENT) throw FileNotFoundException("Only content URIs")
        return context.contentResolver.openInputStream(parsed) ?: throw FileNotFoundException("Not readable")
    }

    override fun displayName(uri: String): String? {
        val parsed = uri.toUri()
        if (parsed.scheme != ContentResolver.SCHEME_CONTENT) return null
        return context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }
}
