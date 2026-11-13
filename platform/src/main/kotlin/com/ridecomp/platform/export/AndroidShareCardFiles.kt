package com.ridecomp.platform.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ridecomp.core.engine.share.ShareCardConfig
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.ShareCardFiles
import java.io.File
import kotlinx.coroutines.withContext

/**
 * Share card PNGs in `cacheDir/share` (12-FEATURE-SPECS §11): app-generated names only, shared with a
 * temporary read grant through the FileProvider (SEC-PLT-04), deleted at the next app start.
 */
class AndroidShareCardFiles(private val context: Context, private val dispatchers: DispatcherProvider) :
    ShareCardFiles {
    private val authority: String = context.packageName + ExportPaths.AUTHORITY_SUFFIX

    // Resolved inside the IO calls: `cacheDir` touches the disk.
    private val directory by lazy { File(context.cacheDir, ExportPaths.SHARE_DIRECTORY) }

    override suspend fun save(fileName: String, png: ByteArray): String = withContext(dispatchers.io) {
        directory.mkdirs()
        File(directory, fileName).apply { writeBytes(png) }.absolutePath
    }

    override suspend fun share(path: String, fileName: String) {
        val uri = withContext(dispatchers.io) { FileProvider.getUriForFile(context, authority, File(path)) }
        val send = Intent(Intent.ACTION_SEND)
            .setType(ShareCardConfig.MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(fileName, uri)
        val chooser = Intent.createChooser(send, fileName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            directory.listFiles()?.filter { it.name.startsWith(ShareCardConfig.FILE_PREFIX) }?.forEach { it.delete() }
        }
    }
}
