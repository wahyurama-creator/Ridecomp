package com.ridecomp.feature.training.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import com.ridecomp.core.source.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * Decodes a picked photo (content URI from the Photo Picker, read-only, no permission) scaled down by powers
 * of two so its longest side is about [SharePhotoLoader.load]'s limit: a 12 MP photo never lands in memory
 * at full size (MEM-06). Null when the file cannot be read.
 */
class AndroidSharePhotoLoader(private val context: Context, private val dispatchers: DispatcherProvider) :
    SharePhotoLoader {
    override suspend fun load(uri: String, maxSidePx: Int): ImageBitmap? = withContext(dispatchers.io) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return@withContext null
        var sample = 1
        while (longest / (sample * 2) >= maxSidePx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    }
}
